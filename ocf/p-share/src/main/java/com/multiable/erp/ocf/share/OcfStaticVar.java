package com.multiable.erp.ocf.share;

public class OcfStaticVar {
	public static final String appName = "ce01_ocf";
	public static final String Ocf = "ocf";

	public static class OcfEJB {
		public static final String OcfCusEJB = appName + "/OcfCusEJB";
		public static final String OcfSalesInvoiceEJB = appName + "/OcfSalesInvoiceEJB";
		public static final String OcfReceiptRegisterEJB = appName + "/OcfReceiptRegisterEJB";
		public static final String OcfDnUploadEJB = appName + "/OcfDnUploadEJB";
		public static final String OcfDnEJB = appName + "/OcfDnEJB";
		public static final String OcfCommonEJB = appName + "/OcfCommonEJB";
	}

	public static class OcfSLAVE {
		public static final String OcfMessageContentSlave = appName + "/OcfMessageContentSlave";
	}

}
