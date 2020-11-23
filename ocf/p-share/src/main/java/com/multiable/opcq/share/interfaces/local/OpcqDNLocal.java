package com.multiable.opcq.share.interfaces.local;

import java.util.Date;

import com.multiable.core.share.data.SqlTable;

public interface OpcqDNLocal {
	public SqlTable loadChargeDiscount(Long beId, Long cusId, Long transAccId, Long invDiscAccId, Date tDate,
			String zipcode, SqlTable dnt);
}
