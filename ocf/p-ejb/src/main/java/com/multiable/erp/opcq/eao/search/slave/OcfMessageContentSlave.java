package com.multiable.erp.opcq.eao.search.slave;

import javax.ejb.Local;
import javax.ejb.Stateless;

import com.multiable.core.ejb.eao.bean.search.SearchSlave;
import com.multiable.core.ejb.eao.bean.search.SearchSlaveAdapter;
import com.multiable.core.share.meta.search.StParameter;
import com.multiable.erp.core.share.util.MacUtil;
import com.multiable.opcq.share.OcfStaticVar.OcfSLAVE;

@Stateless(name = OcfSLAVE.OcfMessageContentSlave)
@Local(SearchSlave.class)
public class OcfMessageContentSlave extends SearchSlaveAdapter {

	@Override
	public void beforeDatalookup(StParameter param) {
		if (MacUtil.isIn(param.getStSearch(), "opcqmessagecontent", "opcqsender")) {
			param.addExtraField("desc");
		}
	}
}
