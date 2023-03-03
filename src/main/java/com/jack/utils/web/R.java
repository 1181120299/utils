package com.jack.utils.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
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
public class R extends HashMap<String, Object> {

	private static final long serialVersionUID = 1L;

	public static final String CODE = "retCode";
	public static final String MSG = "retMsg";
	public static final String DATA = "data";

	public static final int CODE_OK = 2000;
	public static final int CODE_ERROR = 2999;
	
	public R() {
		put(CODE, CODE_OK);
		put(MSG, "请求成功");
		put(DATA, new JSONObject());
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

	public R setData(Object data) {
		put(DATA, data);
		return this;
	}

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

	public String getMsg() {
		return (String) get(MSG);
	}

	public R setMsg(String msg) {
		put(MSG, msg);
		return this;
	}

	public int getCode() {
		return (int) get(CODE);
	}

	public R setCode(int code) {
		put(CODE, code);
		return this;
	}
}
