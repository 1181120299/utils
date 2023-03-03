package com.jack.utils.web;

/**
 * 业务异常
 * 
 * @author chenjiabao
 */
public class RRException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
    private String msg;
    private int code = R.CODE_ERROR;

	/**
	 * 此构造器不应直接调用，只是用于反射。
	 * <P></P>
	 * 业务异常应提供具体错误信息。
	 * @see RRException#RRException(String)
	 * @see RRException#RRException(String, int)
	 * @see RRException#RRException(String, Throwable)
	 * @see RRException#RRException(String, int, Throwable)
	 */
	@Deprecated
	public RRException() {}

    public RRException(String msg) {
		super(msg);
		this.msg = msg;
	}
	
	public RRException(String msg, Throwable e) {
		super(msg, e);
		this.msg = msg;
	}
	
	public RRException(String msg, int code) {
		super(msg);
		this.msg = msg;
		this.code = code;
	}
	
	public RRException(String msg, int code, Throwable e) {
		super(msg, e);
		this.msg = msg;
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}
}
