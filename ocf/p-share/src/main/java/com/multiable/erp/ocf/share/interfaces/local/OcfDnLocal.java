package com.multiable.erp.ocf.share.interfaces.local;

import java.util.Date;
import java.util.List;

import com.multiable.core.share.data.SqlTable;

public interface OcfDnLocal {
	public SqlTable loadChargeDiscount(Long beId, Long cusId, Long transAccId, Long invDiscAccId, Date tDate,
			String zipcode, SqlTable dnt);

	public double getCusDiscount(Long beId, Long cusId);

	public List<SqlTable> calcCusDiscount(Long beId, Long cusId, Long transAccId, Date tDate, String zipcode,
			SqlTable dnt);

	public SqlTable getZipCodeCharge(Long beId, Long transAccId, Date tDate, String zipcode);

	public SqlTable getDefaultPrintSetting(String providerCode);

	public double getHolidayCharge(long beId, Date tDate);
}
