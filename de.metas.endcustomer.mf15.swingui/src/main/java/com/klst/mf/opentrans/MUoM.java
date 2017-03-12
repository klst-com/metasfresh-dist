/*
 * #%L
 * de.metas.endcustomer.mf15.swingui
 * %%
 * Copyright (C) 2016 klst GmbH
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

import java.util.Properties;

import org.compiere.model.I_C_UOM;
import org.compiere.model.MUOM;

public class MUoM extends MUOM
{
	
	private static final long serialVersionUID = 3601334593020526078L;

	public static MUOM getOrCreate(Properties ctx, String name, String trxName) {
		// MUOM:get() wurde als deprecated markiert, ohne dafür einen Ersatz bereitzustellen
		// eint Teil der Begründung: es wird kaum genutzt (nur in AIT) - ist fadenscheinig
		MUOM unit = get(ctx, name, trxName);
		if(unit==null) {
			unit = new MUoM(ctx, name, trxName);
			if(unit.save()) {
				unit.load(trxName);
			}
		}
		return unit;
	}
	
	public MUoM(Properties ctx, int C_UOM_ID, String trxName) {
		super(ctx, C_UOM_ID, trxName);
	}

	public MUoM(Properties ctx, String unit, String trxName) {
		super(ctx, 0, trxName);
		this.log.info("ctor new unit={} in table {}", unit, I_C_UOM.Table_Name); 
		this.set_Value(I_C_UOM.COLUMNNAME_X12DE355, unit);
		this.set_Value(I_C_UOM.COLUMNNAME_Name, unit);
		this.set_Value(I_C_UOM.COLUMNNAME_Description, "used by openTRANS");
	}

}
