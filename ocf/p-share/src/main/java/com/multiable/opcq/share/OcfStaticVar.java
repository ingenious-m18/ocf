package com.multiable.opcq.share;

public class OcfStaticVar {
	public static final String appName = "ce01_ocf";
	public static final String Ocf = "ocf";

	public static class OcfEJB {
		public static final String OpcqRemcusEJB = appName + "/OpcqRemcusEJB";
		public static final String OpcqSalesInvoiceEJB = appName + "/OpcqSalesInvoiceEJB";
		public static final String OpcqReceiptRegisterEJB = appName + "/OpcqReceiptRegisterEJB";
		public static final String OcfDnUploadEJB = appName + "/OcfDnUploadEJB";
	}

	public static class OcfSLAVE {
		public static final String OcfMessageContentSlave = appName + "/OcfMessageContentSlave";
	}

}
