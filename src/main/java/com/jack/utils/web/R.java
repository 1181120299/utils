package com.jack.utils.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jack.utils.config.MapperProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 返回数据格式
 * 
 * @author chenjiabao
 */
@Slf4j
public class R extends HashMap<String, Object> implements ApplicationContextAware {

	private static final long serialVersionUID = 1L;

	private static String CODE = "retCode";
	private static String MSG = "retMsg";
	private static String DATA = "data";

	private static int CODE_OK = 2000;
	private static int CODE_ERROR = 2999;

	private ApplicationContext applicationContext;
	
	public R() {
		put(CODE, CODE_OK);
		put(MSG, "请求成功");
		put(DATA, new JSONObject());
	}

	@EventListener(ContextRefreshedEvent.class)
	public void processStartedEvent() {
		MapperProperties mapperProperties = applicationContext.getBean(MapperProperties.class);

		if (StringUtils.isNotBlank(mapperProperties.getResponseCodeField())) {
			R.CODE = mapperProperties.getResponseCodeField().trim();
		}

		if (StringUtils.isNotBlank(mapperProperties.getResponseMessageField())) {
			R.MSG = mapperProperties.getResponseMessageField().trim();
		}

		if (StringUtils.isNotBlank(mapperProperties.getResponseDataField())) {
			R.DATA = mapperProperties.getResponseDataField().trim();
		}

		if (mapperProperties.getResponseCorrectCode() != null) {
			R.CODE_OK = mapperProperties.getResponseCorrectCode();
		}

		if (mapperProperties.getResponseErrorCode() != null) {
			R.CODE_ERROR = mapperProperties.getResponseErrorCode();
		}
	}
	
	public static R error() {
		return error(CODE_ERROR, "未知异常，请联系管理员");
	}
	
	public static R error(String msg) {
		return error(CODE_ERROR, msg);
	}
	
	public static R error(int code, String msg) {
		R r = new R();
		r.put(CODE, code);
		r.put(MSG, msg);
		return r;
	}

	public static R ok(String msg) {
		R r = new R();
		r.put(MSG, msg);
		return r;
	}

	public static R ok(Map<String, Object> map) {
		R r = new R();
		r.putAll(map);
		return r;
	}
	
	public static R ok() {
		return new R();
	}

	public R put(String key, Object value) {
		super.put(key, value);
		return this;
	}

	/**
	 * 设置接口的响应数据
	 * @param data	数据
	 * @return	响应格式封装类
	 */
	public R setData(Object data) {
		put(DATA, data);
		return this;
	}

	/**
	 * 获取接口的响应数据
	 * @return	数据
	 */
	public Object getData() {
		return get(DATA);
	}

	/**
	 * 返回data数据，封装成指定的实体类
	 * @param clazz	实体类的class
	 * @return	实体类
	 */
	public <T> T getData(Class<T> clazz) {
		Assert.notNull(clazz, "clazz can not be null");

		Object obj = get(DATA);
		if (Objects.isNull(obj)) {
			return null;
		}

		return JSON.parseObject(JSON.toJSONString(obj), clazz);
	}

	/**
	 * 返回data数据，封装成集合
	 * @param clazz	集合元素的class
	 * @return	数据集合
	 */
	public <T> List<T> getDataList(Class<T> clazz) {
		Assert.notNull(clazz, "clazz can not be null");

		Object obj = get(DATA);
		if (Objects.isNull(obj)) {
			return null;
		}

		log.debug("===>clazz = {}, obj.getClass() = {}", clazz.getSimpleName(), obj.getClass().getSimpleName());
		if (!List.class.isAssignableFrom(obj.getClass())
				&& !obj.getClass().isArray()) {
			throw new IllegalArgumentException(DATA + " is not an array or list");
		}

		return JSON.parseArray(JSON.toJSONString(obj), clazz);
	}

	/**
	 * 获取接口的提示信息
	 * @return	提示信息
	 */
	public String getMsg() {
		return (String) get(MSG);
	}

	/**
	 * 设置接口的提示信息
	 * @param msg	提示信息
	 * @return	响应格式封装类
	 */
	public R setMsg(String msg) {
		put(MSG, msg);
		return this;
	}

	/**
	 * 获取接口的响应状态码
	 * @return	状态码
	 */
	public int getCode() {
		return (int) get(CODE);
	}

	/**
	 * 设置接口的响应状态码
	 * @param code	状态码
	 * @return	响应格式封装类
	 */
	public R setCode(int code) {
		put(CODE, code);
		return this;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * 返回定义的正常状态码
	 * @return	正常状态码
	 */
	public static int getCodeOk() {
		return CODE_OK;
	}

	/**
	 * 返回定义的错误状态码
	 * @return	错误状态码
	 */
	public static int getCodeError() {
		return CODE_ERROR;
	}
}
