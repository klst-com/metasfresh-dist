/*
 * #%L
 * de.metas.endcustomer.mf15.swingui
 * %%
 * Copyright (C) 2017 klst GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package com.klst.mf.opentrans;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DB;
import org.compiere.util.Env;

public class MPriceListVersion extends org.compiere.model.MPriceListVersion
{

	private static final String SQL_SOPRICELIST = "SELECT m_pricelist_id FROM m_pricelist"
			+ " WHERE isactive='Y' AND ad_client_id = ? AND ad_org_id IN( 0, ? ) AND name = ? AND issopricelist='Y'";
	private static final String SQL_SOPRICELISTVERSION = "SELECT * FROM m_pricelist_version"
			+ " WHERE isactive='Y' AND m_pricelist_id in (" + SQL_SOPRICELIST + ")";
	
	private static final long serialVersionUID = -2452173477803977735L;

	//private PreparedStatement pstmtPricelistVersion;  
	
	// std ctor
	public MPriceListVersion(Properties ctx, int M_PriceList_Version_ID, String trxName) {
		super(ctx, M_PriceList_Version_ID, trxName);
		//pstmtPricelistVersion = DB.prepareStatement(SQL_SOPRICELISTVERSION, trxName);
	}
	
	// Load Constructor
	public MPriceListVersion(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		if(this.is_JustCreated()) {
			log.warn("ctor: MPriceListVersion is_JustCreated rs={}", rs);
		}
	}

	/* holt default plv
	 * 
	 */
	public static MPriceListVersion getDefaultSOPriceListVersion(Properties ctx, String trxName) {
		MPriceListVersion plv = null;
		PreparedStatement pstmtPricelistVersion = DB.prepareStatement(SQL_SOPRICELISTVERSION, trxName);
		ResultSet rs;
		try {
			pstmtPricelistVersion.setInt(1, Env.getAD_Client_ID(ctx));
			pstmtPricelistVersion.setInt(2, Env.getAD_Org_ID(ctx));
			pstmtPricelistVersion.setString(3, "SOE");
			rs = pstmtPricelistVersion.executeQuery();
			if(rs.next()) {
				plv = new MPriceListVersion(ctx, rs, trxName);
				if(plv.is_JustCreated()) {
					throw new AdempiereException("cannot find a DefaultSOPriceList SOE");
				}
			} else {
				throw new AdempiereException("cannot find a DefaultSOPriceList SOE");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return plv;
	}
}
