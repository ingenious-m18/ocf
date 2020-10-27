package com.multiable.erp.opcq.eao.bean;

import java.util.Date;

import javax.ejb.Local;
import javax.ejb.Stateless;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.lib.DateLib;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OpcqRemcusLocal;

@Stateless(name = OcfEJB.OpcqRemcusEJB)
@Local({ OpcqRemcusLocal.class })
public class OpcqRemcusEJB implements OpcqRemcusLocal {
	@Override
	public SqlTable getCoupon(String idStr) {
		String sql = "select * from opcqredemptioncoupon where id in (" + idStr + ");";

		SqlTable result = CawDs.getResult(sql);
		return result;

	}

	@Override
	public SqlTable getRedemptionPointNeed(Long id, Date eDate) {
		String sql = "select a.id, b.`eDate`, b.redemptionpointneeded from opcqredemptioncoupon a, opcqredemptioncoupont b where a.id = b.hId "
				+ " and a.id = " + id + " and b.`eDate` <= '" + DateLib.dateToString(eDate)
				+ "' and not exists(select 1 from opcqredemptioncoupont t where t.hId = b.hId and t.`eDate` > b.`eDate`)";

		SqlTable result = CawDs.getResult(sql);
		return result;
	}
}
