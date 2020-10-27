package com.multiable.opcq.share.interfaces.local;

import java.util.Date;

import com.multiable.core.share.data.SqlTable;

public interface OpcqRemcusLocal {
	public SqlTable getCoupon(String idStr);

	public SqlTable getRedemptionPointNeed(Long id, Date eDate);
}
