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

package com.klst.mf.opentrans.process;

import org.adempiere.exceptions.AdempiereException;
import org.opentrans.xmlschema._2.ORDER;

import com.klst.mf.opentrans.MOrder;

/*
 * wird nicht von JavaProcess abgeleitet, da mit CreateProductProcess vieles gemeinsam genutzt wird
 * 
 * prepare() und doIt() aus super!
 */
public class CreateOrderProcess extends CreateProductProcess
{

	/*
	 * Perform process (aus super) verarbeitet mehrere files und ruft doOne für ein opentrans-ORDER
	 * 
	 * @return Message
	 * @throws Exception
	 */	
	@Override
	protected String doOne(String msg, String uri) throws Exception {
		
		String ret = uri;
		ORDER order = unmarshal(uri);
		
		try {
			MOrder morder = MOrder.mapping(this.getCtx(), order, pDropShipBPartner_ID, pSalesRep_ID, this.get_TrxName());
			ret = ret + " mapped to " + morder;
		} catch (Exception e) {
			e.printStackTrace();
			log.warn("doOne: {}",e.getMessage());
			throw new AdempiereException(e.getMessage() + " in "+uri );
		}
		
		return ret;
	}

}
