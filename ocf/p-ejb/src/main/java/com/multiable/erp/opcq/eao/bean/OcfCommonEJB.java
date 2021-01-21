package com.multiable.erp.opcq.eao.bean;

import javax.ejb.Local;
import javax.ejb.Stateless;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.share.data.SqlTable;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OcfCommonLocal;

@Stateless(name = OcfEJB.OcfCommonEJB)
@Local({ OcfCommonLocal.class })
public class OcfCommonEJB implements OcfCommonLocal {
	@Override
	public SqlTable getOcfTerms(Long beId) {
		String sql = "select * from ocfterms where id != 0 and beId = " + beId;

		SqlTable result = CawDs.getResult(sql);
		return result;

	}
}
