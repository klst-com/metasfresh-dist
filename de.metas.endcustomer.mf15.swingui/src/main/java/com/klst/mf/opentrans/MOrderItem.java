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

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.pricing.api.IPriceListDAO;
import org.adempiere.util.Services;
import org.bmecat.bmecat._2005.DESCRIPTIONSHORT;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProductPO;
import org.compiere.model.MProductPrice;
import org.compiere.model.MUOM;
import org.compiere.util.DB;
import org.opentrans.xmlschema._2.ORDERITEM;
import org.opentrans.xmlschema._2.PRODUCTID;
import org.opentrans.xmlschema._2.PRODUCTPRICEFIX;
import org.opentrans.xmlschema._2.TAXDETAILSFIX;

import de.metas.adempiere.model.I_M_ProductPrice;

public class MOrderItem extends MOrderLine
{

	private static final long serialVersionUID = 1687138425318329740L;

	private static final String SQL_PRODUCT_PO = "SELECT m_product_id FROM m_product_po"
			+ " WHERE isactive='Y' AND c_bpartner_id = ? ";
	private static final String SQL_PRODUCT = "SELECT m_product_id FROM m_product"
			+ " WHERE isactive='Y' AND sku like ? and m_product_id IN(" + SQL_PRODUCT_PO + ")";
	private PreparedStatement pstmtProduct; // sucht ein Produkt
	
	ORDERITEM otItem = null;
	int dropShipBPartner_ID = -1;
	
	// ctor
	public MOrderItem(Properties ctx, int C_OrderLine_ID, String trxName) {
		super(ctx, C_OrderLine_ID, trxName);
		pstmtProduct = DB.prepareStatement(SQL_PRODUCT, trxName);
	}

	public MOrderItem(MOrder order, ORDERITEM item, int C_OrderLine_ID, int dropShipBPartner_ID) {
		this(order.getCtx(), C_OrderLine_ID, order.get_TrxName());
		if(order.get_ID() == 0)
			throw new IllegalArgumentException("MOrder not saved");
		
		log.info("ctor Line={} C_Order_ID={} Product={}"
				, this.getLine(), this.getC_Order_ID(), this.getProduct() );
		if(C_OrderLine_ID==0) {
			setC_Order_ID(order.getC_Order_ID());	//	parent
			setOrder(order); // ist mehr als setHeaderInfo(order)
			setLine(Integer.parseInt(item.getLINEITEMID())); // Exception bei parseInt
		}
		this.otItem = item;
		this.dropShipBPartner_ID = dropShipBPartner_ID;
	}

	/*
	 * zum mappen hat man in otProduct nur SUPPLIERPID / ==> in SKU hinter '::' PGI525BK::101748090 
	 * und DESCRIPTION_SHORT : ==> name
	 * 
	 * den Rest muss man sich zusammensuchen
	 */
	public MProduct mapProduct() {
		MProduct product = null;
		PRODUCTID otProduct = this.otItem.getPRODUCTID(); // mandatory
		String vendorProductNo = null;
		if(otProduct.getSUPPLIERPID()==null) {
			throw new AdempiereException("No SUPPLIERPID" + " in item "+this.otItem.getLINEITEMID() );
		} else {
			vendorProductNo = otProduct.getSUPPLIERPID().getValue();
		}
		String desc0 = null;
		List<DESCRIPTIONSHORT> descList = otProduct.getDESCRIPTIONSHORT();
		if(descList==null) {
			// exception ? 
		} else {
			if(descList.size()==0) {
				// exception ?
			} else {
				desc0 = descList.get(0).getValue();
			}
		}
		PRODUCTPRICEFIX otPrice = this.otItem.getPRODUCTPRICEFIX();
		
		List<MProduct> pl = getProduct(vendorProductNo, this.dropShipBPartner_ID);	
		if(pl.size()!=1) {
			// == 0 : darf nicht sein, weil CreateProductProcess gelafen sein muss!
			//  > 1 : nicht eindeutig (könnte man noch korrigieren : TODO )
			throw new AdempiereException(" not unique! Product '" + vendorProductNo + "' result.size="+pl.size());
		}
		product = pl.get(0); 
		log.info("mapProduct: *VendorProductNo={} product={} desc0={}", vendorProductNo, product, desc0 );
		BigDecimal pricepp = null;
		BigDecimal tax = null;
		if(otPrice==null) {
			throw new AdempiereException("No PRODUCTPRICE" + " in item "+this.otItem.getLINEITEMID() );
		} else {
			// PRICE_QUANTITY berücksichtigen
			pricepp = (otPrice.getPRICEQUANTITY()==null || otPrice.getPRICEQUANTITY().signum()==0) ? otPrice.getPRICEAMOUNT() 
					: otPrice.getPRICEAMOUNT().divide(otPrice.getPRICEQUANTITY());

			List<TAXDETAILSFIX> taxes = otPrice.getTAXDETAILSFIX();
			for(Iterator<TAXDETAILSFIX> it = taxes.iterator(); it.hasNext(); ) {
				TAXDETAILSFIX taxfix = it.next();
				if("VAT".equals(taxfix.getTAXTYPE())) {
					tax = taxfix.getTAX();
				}
			}
		}
		
		// bis hierhin wurde nix gemappt.
		if(desc0!=null) {
//			product.setName(desc0);	// keine Korrekturen
		}
		
		// TODO in mf ist das DM anders: tax hängt an m_productprice.c_taxcategory_id
//		if(tax==null) {
//			product.setC_TaxCategory_ID(product.getDefaultTaxCategory().getC_TaxCategory_ID());
//		} else {
//			// Bsp in order_LS3_31234_8689_2014-10-17-.441.xml
//			product.setC_TaxCategory_ID(product.getTaxCategory(tax).getC_TaxCategory_ID());
//		}

		MUOM unit = MUoM.getOrCreate(this.getCtx(), this.otItem.getORDERUNIT(), this.get_TrxName());
		// wg.  UOM can't be changed if the product has movements or costs
//		product.setC_UOM_ID(unit.getC_UOM_ID());
		
		product.setIsDropShip(MOrder.ISDROPSHIP);
		
		// mierp-Besonderheit:
//		product.set_ValueOfColumnReturningBoolean(MProduct.COLUMNNAME_priceso, pricepp);
//		product.set_ValueOfColumnReturningBoolean(MProduct.COLUMNNAME_vendor_id, dropShipBPartner_ID);
//		product.saveEx(this.get_TrxName());
		
		
		// pPO: auf mierp darf es nur einen geben!
		MProductPO pPO = product.findOrCreateMProductPO(dropShipBPartner_ID, vendorProductNo);
		pPO.setC_UOM_ID(unit.getC_UOM_ID());
// TODO	pPO.setC_Currency_ID(C_Currency_ID);
		pPO.setPriceList(pricepp);
		pPO.setPriceLastInv(pricepp);
		pPO.setVendorProductNo(vendorProductNo);
		pPO.saveEx(this.get_TrxName());

		MPriceListVersion plv = MPriceListVersion.getDefaultSOPriceListVersion(getCtx(), get_TrxName());
		if(plv==null) {
			log.error("mapProduct: No MPriceListVersion");
			throw new AdempiereException("cannot find a DefaultSOPriceList SOE");
		} else {
			log.info("mapProduct: plv.M_PriceList_Version_ID={}",plv.getM_PriceList_Version_ID());
		}
		int plvID = plv.getM_PriceList_Version_ID();
		final I_M_ProductPrice pp = Services.get(IPriceListDAO.class).retrieveProductPriceOrNull(plv, product.getM_Product_ID());
		MProductPrice price = null;
		if(pp != null) {
			price = (MProductPrice)InterfaceWrapperHelper.getPO(pp);
		}
		if(price == null) {
			price = new MProductPrice(getCtx(), plvID, product.getM_Product_ID(), get_TrxName());
		}
		price.setPrices(pricepp, pricepp, pricepp); // (PriceList, PriceStd, PriceLimit)		
		price.saveEx(this.get_TrxName());
		
		this.setProduct(product);
		this.setPrice(pricepp); // Use this Method if the Line UOM is the Product UOM 
		product.saveEx(this.get_TrxName());
		
//		if(tax==null) {
//			this.setC_TaxCategory(product.getDefaultTaxCategory());
//		} else {
//			this.setC_TaxCategory(product.getTaxCategory(tax));
//		}

		return product;
	}

	/* zum Finden hat man nur SUPPLIERPID / in SKU hinter '::' , Bsp PGI525BK::101748090 
	 * sollte genau ein Produkt liefern, oder nix. Bei mehr bleibt nur exception über
	 * 
	 * not unique! Product '337014401' result.size=2 in C:\proj\minhoff\input\order_LS3_31234_8659_2014-10-10-.013
	 */
	private List<MProduct> getProduct(String otProductSupplierPid, int dropShipBPartner_ID) {
		List<MProduct> resultList = null;
		ResultSet rs;
		int M_Product_ID = -1; 
		resultList = new ArrayList<MProduct>();
		try {
			pstmtProduct.setString(1, "%"+otProductSupplierPid);
			pstmtProduct.setInt(2, dropShipBPartner_ID);
			rs = pstmtProduct.executeQuery();
			log.info("getProduct: loop through ResultSet params '{}' '{}'\nsql={}"
					, "%"+otProductSupplierPid, dropShipBPartner_ID, SQL_PRODUCT);
			while (rs.next()) {
				M_Product_ID = rs.getInt(1);
				log.info("getProduct: M_Product_ID={}  otProductSupplierPid=={}", M_Product_ID, otProductSupplierPid);
				resultList.add(new MProduct(this.getCtx(), M_Product_ID, this.get_TrxName()));
			}
			if(resultList.size()>1) {
				log.warn("getProduct: not unique! Product '{}' result.size={}",  otProductSupplierPid, resultList.size());
			}
			if(resultList.isEmpty()) {
				log.info("getProduct: not found! Product with otProductSupplierPid='{}'", otProductSupplierPid);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return resultList;
	}

}
