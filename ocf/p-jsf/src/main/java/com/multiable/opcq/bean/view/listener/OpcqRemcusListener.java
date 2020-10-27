package com.multiable.opcq.bean.view.listener;

import java.util.Date;

import javax.faces.component.UIComponent;

import com.multiable.bean.view.ModuleAction;
import com.multiable.bean.view.ModuleAction.ActionParam;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.data.SqlTableField;
import com.multiable.core.share.lib.ConvertLib;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.erp.core.bean.listener.MacModuleRecordViewListener;
import com.multiable.erp.core.bean.util.MacWebUtil;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;
import com.multiable.logging.CawLog;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OpcqRemcusLocal;
import com.multiable.web.ValueChangeEvent;
import com.multiable.web.component.edittable.EditTable;
import com.multiable.web.component.edittable.EditTableModel;

public class OpcqRemcusListener extends MacModuleRecordViewListener {

	@Override
	public void afterCreated(ModuleAction action) {
		super.afterCreated(action);
		prepareData(action);
	}

	@Override
	public void afterRead(ModuleAction action) {
		super.afterRead(action);
		if ((boolean) action.getDataOrDefault(ActionParam.status, false)) {
			prepareData(action);
		}
	}

	@Override
	public void afterRefresh(ModuleAction action) {
		super.afterRefresh(action);
		prepareData(action);
	}

	private void prepareData(ModuleAction action) {
		// Get table from getEntity()
		SqlTable ocfrccus = getEntity().getData("ocfrccus");
		ocfrccus.addField(new SqlTableField("rcdesc", String.class));

		// Assign rcdesc
		// Call OpcqRemcusEJB
		OpcqRemcusLocal remcusEJB = null;
		try {
			remcusEJB = JNDILocator.getInstance().lookupEJB(OcfEJB.OpcqRemcusEJB, OpcqRemcusLocal.class);
		} catch (Exception e) {
			CawLog.error(e);
		}

		// Get coupon id str from ocfrccus
		String idStr = "";
		for (int i = 1; i <= ocfrccus.size(); i++) {
			idStr = idStr + ocfrccus.getLong(i, "rccode") + ",";
		}

		SqlTable result = null;
		if (idStr.length() > 0) {
			idStr = idStr.substring(0, idStr.length() - 1);

			// Use OpcqRemcusEJB
			result = remcusEJB.getCoupon(idStr);

			// Assign desc
			TableStaticIndexAdapter couponIndex = new TableStaticIndexAdapter(result) {
				@Override
				public String getIndexKey() {
					return src.getValueStr(srcRow, "id");
				}
			};
			couponIndex.action();

			for (int i = 1; i <= ocfrccus.size(); i++) {
				int seekRow = couponIndex.seek(ocfrccus.getLong(i, "rccode") + "");
				if (seekRow > 0) {
					ocfrccus.setString(i, "rcdesc", result.getString(seekRow, "desc"));
				}
			}
		}

	}

	@Override
	public void valueChange(ValueChangeEvent vce) {
		UIComponent component = vce.getComponent();
		String comId = component.getId();

		if (component instanceof EditTable) {
			EditTable eTable = (EditTable) vce.getComponent();
			String tableName = eTable.getTableName();
			String columnName = eTable.getCellColumnName();
			EditTableModel tableModel = getTableModel(tableName);
			int rowIndex = tableModel.getRowIndex(eTable.getCellRowid());

			if (tableName.equals("ocfrccus")) {
				if (columnName.equals("rccode")) {
					// Get lookup result
					SqlTable lookupResult = eTable.getLookupResult();

					if (lookupResult != null && lookupResult.size() > 0) {
						// Assign desc
						String desc = lookupResult.getString(1, "desc");

						// Get table ocfrccus
						SqlTable ocfrccus = getEntity().getData("ocfrccus");
						ocfrccus.setString(rowIndex, "rcdesc", desc);

						MacWebUtil.reloadEditTable("ocfrccus");
					}
				} else if (columnName.equals("rcquantity")) {
					// Get Coupon redemption point need (based on effective date)
					OpcqRemcusLocal remcusEJB = null;
					try {
						remcusEJB = JNDILocator.getInstance().lookupEJB(OcfEJB.OpcqRemcusEJB,
								OpcqRemcusLocal.class);
					} catch (Exception e) {
						CawLog.error(e);
					}

					SqlTable ocfrccus = getEntity().getData("ocfrccus");
					Long id = ocfrccus.getLong(rowIndex, "rccode");
					Date eDate = (Date) ocfrccus.getObject(rowIndex, "rcdate");
					SqlTable couponResult = remcusEJB.getRedemptionPointNeed(id, eDate);

					// Calculation
					if (couponResult != null && couponResult.size() > 0) {

						double pointNeed = couponResult.getDouble(1, "redemptionpointneeded");
						double pointSpent = ConvertLib.toDouble(vce.getNewValue()) * pointNeed;

						// Assign rcpointspent
						ocfrccus.setDouble(rowIndex, "rcpointspent", pointSpent);
					}

				}
			}

		}

	}

	@Override
	public String getFooterLookupType(String tableName, int rowIndex, String columnName) {
		if (tableName.equals("ocfremcust")) {
			SqlTable ocfremcust = getEntity().getData("ocfremcust");
			if (columnName.equals("sourceId")) {

				return ocfremcust.getString(rowIndex, "sourceType");
			}
		} else if (tableName.equals("ocfrcdcus")) {
			SqlTable ocfrcdcus = getEntity().getData("ocfrcdcus");
			if (columnName.equals("rcdsourcetransaction")) {

				return ocfrcdcus.getString(rowIndex, "rcdsourcetype");
			}
		}

		return super.getFooterLookupType(tableName, rowIndex, columnName);
	}
}
