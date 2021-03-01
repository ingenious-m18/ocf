package com.multiable.erp.ocf.share.message;

import javax.ws.rs.core.Response.Status;

import com.multiable.core.share.message.ICawMsg;

public enum OcfAccountError implements ICawMsg {
	DUP_TRANSCHARGE(100001, Status.BAD_REQUEST, "Duplicate Transport Charge", "ocf.dupTransChargeMsg"),

	DUP_TRANSCHARGEINVOICEDISCOUNT(
			100002,
			Status.BAD_REQUEST,
			"Duplicate Transport Charge And Invoice Discount",
			"ocf.dupTransChargeInvoiceDiscountMsg"),
	DUP_INVOICEDISCOUNT(100003, Status.BAD_REQUEST, "Duplicate Invoice Discount", "ocf.dupInvoiceDiscMsg");

	private int errorId;
	private Status status;
	private String info = "";
	private String mess = "";
	private static String errorKey = "ce01_ocf_account_";

	OcfAccountError(int errorId, Status status, String info, String mess) {
		this.errorId = errorId;
		this.status = status;
		this.info = info;
		this.mess = mess;
	}

	@Override
	public int getErrorId() {
		return errorId;
	}

	public void setErrorId(int errorId) {
		this.errorId = errorId;
	}

	@Override
	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	@Override
	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

	@Override
	public String getMess() {
		return mess;
	}

	public void setMess(String mess) {
		this.mess = mess;
	}

	@Override
	public String getMsgKey() {
		return errorKey + errorId;
	}
}
