package com.multiable.erp.ocf.share.interfaces.local;

import java.util.Date;

import com.multiable.core.share.data.SqlTable;

public interface OcfCusLocal {
	public SqlTable getCoupon(String idStr);

	public SqlTable getRedemptionPointNeed(Long id, Date eDate);
}
