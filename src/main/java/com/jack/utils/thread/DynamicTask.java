package com.jack.utils.thread;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 动态创建定时任务的工具类。支持对定时任务进行更新，比如重新指定cron表达式。
 * <p></p>
 * 使用示例：
 * <blockquote><pre>
 *     //依赖注入 @Autowired
 *     private DynamicTask dynamicTask;
 *
 *     String cron = "0 0 18 * * ?";
 *
 *     DynamicTask.TaskConstant taskConstant = new DynamicTask.TaskConstant(taskId -> doSomething());
 * 	   taskConstant.setCron(cron);
 * 	   taskConstant.setTaskId("任务id，任意值，不同任务间id不重复就行");
 * 	   taskConstant.setRule("周期性拉取称重数据");
 * 	   dynamicTask.addTask(taskConstant);
 * </pre></blockquote>
 * 示例中的doSomething()方法，就可以定义当定时任务执行时的业务逻辑。
 * <p></p>
 * 如果要更新定时任务的cron表达式，只需再次添加任务即可（任务id需要保持一致）。类似于上面的例子。
 */
@Component
@Slf4j
@EnableScheduling
public class DynamicTask implements SchedulingConfigurer {

    /**
     * 触发器触发周期，单位：秒。此参数决定了在定时任务的cron表达式发生变化之后，新的cron表达式生效所需要的最大时间。
     *
     */
    private static final long triggerPeriod = 5L;

    /**
     * corePoolSize = 0，maximumPoolSize = Integer.MAX_VALUE，即线程数量几乎无限制；
     * keepAliveTime = 60s，线程空闲60s后自动结束。
     * workQueue 为 SynchronousQueue 同步队列，这个队列类似于一个接力棒，入队出队必须同时传递，因为CachedThreadPool线程创建无限制，不会有队列等待，所以使用SynchronousQueue；
     */
    private static final ExecutorService es = new ThreadPoolExecutor(10,
            10,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>());


    private volatile ScheduledTaskRegistrar registrar;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CronTask> cronTasks = new ConcurrentHashMap<>();

    private final Set<TaskConstant> taskConstantSet = new LinkedHashSet<>();

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        this.registrar = registrar;
        this.registrar.addTriggerTask(() -> {
            if (CollectionUtils.isEmpty(taskConstantSet)) {
                return;
            }

            // 移除过期的cron表达式配置
            Map<String, Long> taskIdCountingMap = taskConstantSet.stream()
                 .collect(Collectors.groupingBy(TaskConstant::getTaskId, Collectors.counting()));

            taskIdCountingMap.forEach((taskId, counting) -> {
                if (counting <= 1) {
                    return;
                }

                // 同一个任务id，只保留一条cron表达式
                Iterator<TaskConstant> it = taskConstantSet.iterator();
                while (it.hasNext() && counting > 1) {
                    TaskConstant taskConstant = it.next();
                    if (taskId.equals(taskConstant.getTaskId())) {
                        counting--;
                        it.remove();
                    }
                }
            });

            List<TimingTask> timingTaskList = new ArrayList<>();
            taskConstantSet.forEach(taskConstant -> {
                TimingTask tt = new TimingTask(taskConstant.getConsumer());
                tt.setExpression(taskConstant.getCron());
                tt.setTaskId("dynamic-task-" + taskConstant.getTaskId());
                tt.setRule(taskConstant.getRule());
                timingTaskList.add(tt);
            });

            this.refreshTasks(timingTaskList);
        }
        , triggerContext -> new PeriodicTrigger(triggerPeriod, TimeUnit.SECONDS).nextExecutionTime(triggerContext));
    }

    private void refreshTasks(List<TimingTask> timingTaskList) {
        //取消已经删除的策略任务
        Set<String> taskIds = scheduledFutures.keySet();
        for (String taskId : taskIds) {
            if (!exists(timingTaskList, taskId)) {
                log.info("移除过期配置：taskId = {}", taskId);
                scheduledFutures.remove(taskId).cancel(false);
                cronTasks.remove(taskId);
            }
        }

        for (TimingTask timingTask : timingTaskList) {
            String expression = timingTask.getExpression();
            if (StringUtils.isBlank(expression) || !CronSequenceGenerator.isValidExpression(expression)) {
                log.error("定时任务DynamicTask cron表达式不合法: {}", expression);
                continue;
            }

            CronTask cronTask = cronTasks.get(timingTask.getTaskId());
            if (cronTask != null
                    && !cronTask.getExpression().equals(expression)) {
                //如果策略执行时间发生了变化，则取消当前策略的任务
                scheduledFutures.remove(timingTask.getTaskId()).cancel(false);
                log.info("移除的配置：{}。cron = {}", timingTask.getRule(), cronTask.getExpression());
                cronTasks.remove(timingTask.getTaskId());
            }

            //如果配置一致，则不需要重新创建定时任务
            if (Objects.isNull(cronTasks.get(timingTask.getTaskId()))) {
                log.info("增加的配置：{}。cron = {}", timingTask.getRule(), timingTask.getExpression());
                CronTask task = new CronTask(timingTask, expression);
                ScheduledFuture<?> future = Objects.requireNonNull(registrar.getScheduler()).schedule(task.getRunnable(), task.getTrigger());
                cronTasks.put(timingTask.getTaskId(), task);
                assert future != null;
                scheduledFutures.put(timingTask.getTaskId(), future);
            }
        }
    }

    private boolean exists(List<TimingTask> tasks, String taskId) {
        for (TimingTask task : tasks) {
            if (task.getTaskId().equals(taskId)) {
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    public void destroy() {
        this.registrar.destroy();
    }

    @Data
    public static class TaskConstant {
        /**
         * cron表达式
         */
        private String cron;
        /**
         * 任务id，如果id相同，重复添加时则删除之前添加的。
         */
        private String taskId;
        /**
         * 定时任务的描述信息
         */
        private String rule;
        /**
         * 消费者回调。定时任务执行时要处理的业务逻辑
         */
        private Consumer<String> consumer;

        public TaskConstant(Consumer<String> consumer) {
            Assert.notNull(consumer, "请提供消费者回调");
            this.consumer = consumer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TaskConstant that = (TaskConstant) o;

            if (cron != null ? !cron.equals(that.cron) : that.cron != null) return false;
            return taskId != null ? taskId.equals(that.taskId) : that.taskId == null;
        }

        @Override
        public int hashCode() {
            int result = cron != null ? cron.hashCode() : 0;
            result = 31 * result + (taskId != null ? taskId.hashCode() : 0);
            return result;
        }
    }

    @Data
    @ToString
    private static class TimingTask implements Runnable {
        private String expression;  // cron表达式
        private String taskId;  // 任务id，如果id相同，重复添加时则删除之前添加的。
        private String rule;    // 定时任务的描述信息
        private Consumer<String> consumer;  // 定时任务执行时要处理的业务逻辑，相当于回调

        TimingTask(Consumer<String> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void run() {

            es.submit(() -> {
                //这里写业务方法
                log.info("执行定时任务:{},执行时间：{}，{} ", this.getTaskId(), LocalDateTime.now().toLocalTime(), this.getRule());
                consumer.accept(taskId);
            });

        }
    }


    /**
     * 添加定时任务
     * @param task  定时任务
     * @return  true：添加成功，false：失败。(as specified by {@link Collection#add(Object)})
     */
    public boolean addTask(TaskConstant task) {
        return this.taskConstantSet.add(task);
    }

    /**
     * 删除定时任务
     * @param taskId    定时任务id
     * @return  true：删除成功，false：删除失败
     */
    public void deleteTask(String taskId) {
        Assert.notNull(taskId, "taskId can not be null");

        List<TaskConstant> targetList = this.taskConstantSet.stream()
                .filter(item -> taskId.equals(item.getTaskId()))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(targetList)) {
           log.info("没有对应id的定时任务。taskId = {}", taskId);
           return;
        }

        this.taskConstantSet.removeAll(targetList);
    }
}
