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

package com.klst.mf.opentrans.process;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.pricing.api.ProductPriceQuery;
import org.bmecat.bmecat._2005.DESCRIPTIONSHORT;
import org.compiere.model.I_M_ProductPrice;
import org.compiere.model.MProductPO;
import org.compiere.model.MProductPrice;
import org.compiere.model.MUOM;
import org.compiere.util.DB;
import org.opentrans.xmlschema._2.ORDER;
import org.opentrans.xmlschema._2.ORDERITEM;
import org.opentrans.xmlschema._2.PRODUCTID;
import org.opentrans.xmlschema._2.PRODUCTPRICEFIX;
import org.opentrans.xmlschema._2.TAXDETAILSFIX;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.klst.mf.opentrans.MOrder;
import com.klst.mf.opentrans.MPriceListVersion;
import com.klst.mf.opentrans.MProduct;
import com.klst.mf.opentrans.MUoM;
import com.klst.opentrans.XmlReader;

import de.metas.process.JavaProcess;
import de.metas.process.ProcessInfoParameter;

public class CreateProductProcess extends JavaProcess {

	// TODO getter für die Parameter, vorab geht es auch mit protected:
	protected String pDateipfad = null;
	protected String pDateipfadProcessed = null;
	protected int pDropShipBPartner_ID = -1;
	protected int pSalesRep_ID = -1;

	/**
	 * getParameterAsString für parameter, nicht notwendig in Idempiere, metasfresh
	 * @deprecated
	 */
	protected static String getParameterAsString(Object p) {
		if(p==null)
			return null;
		return p.toString();
	}
	
	/*
	 * Prepare process run. See {@link Param} for a way to avoid having to implement this method.
	 * <b>
	 * Here you would implement process preparation business logic (e.g. parameters retrieval).
	 * <b>
	 * If you want to run this method out of transaction, please annotate it with {@link RunOutOfTrx}. By default, this method is executed in transaction.
	 *
	 * @throws ProcessCanceledException in case there is a cancel request on prepare
	 * @throws RuntimeException in case of any failure
	 */
	@Override
	protected void prepare()
	{
		// parameter holen, es gibt 3 Methoden:
		// 1. List<ProcessInfoParameter> paramsList = getParameters()
		// 2. ProcessInfoParameter[] getParametersAsArray() // nutzt getParameters() - ich nutze diese hier
		// 3 IRangeAwareParams iParams = getParameterAsIParams()
		ProcessInfoParameter[] para = getParametersAsArray();
		log.info("prepare: no of params {}", para.length);
		for (int i = 0; i < para.length; i++) {
			String name = para[i].getParameterName();
			if (name.equals("Dateipfad")) {
				pDateipfad = getParameterAsString(para[i].getParameter());
			} else if(name.equals("DateipfadProcessed")) {
				pDateipfadProcessed = para[i].getParameterAsString();
			} else if(name.equals("C_BPartner_ID")) {
				pDropShipBPartner_ID = para[i].getParameterAsInt();
			} else if(name.equals("SalesRep_ID")) {
				pSalesRep_ID = para[i].getParameterAsInt();
			} else {
				log.error("Unknown Parameter: {}", name);
			}
		}
		log.info("prepare: Dateipfad={} , DateipfadProcessed={}", pDateipfad, pDateipfadProcessed);
		log.info("...    : C_BPartner_ID={} , pSalesRep_ID={}", pDropShipBPartner_ID, pSalesRep_ID);
	}
	
	/*
	 * Actual process business logic to be executed.
	 *
	 * This method is called after {@link #prepare()}.
	 *
	 * If you want to run this method out of transaction, please annotate it with {@link RunOutOfTrx}. By default, this method is executed in transaction.
	 *
	 * @return Message (variables are parsed)
	 * @throws ProcessCanceledException in case there is a cancel request on doIt
	 * @throws Exception if not successful e.g. <code>throw new AdempiereException ("@MyExceptionADMessage@");</code>
	 */
	@Override
	protected String doIt() throws Exception {
		
		String msg="";
		pstmtProduct = DB.prepareStatement(SQL_PRODUCT, get_TrxName());
		
		if(pDateipfad==null || pDateipfadProcessed==null) {
			return "parm error:"+" inPath="+pDateipfad+" , outPath="+pDateipfadProcessed;
		}
		try {
			File dir = new File(pDateipfad);
			File dirto = new File(pDateipfadProcessed);
			String[] files = dir.list(); // no filter
			if(files==null || files.length==0) {
				return "no files:"+" inPath="+pDateipfad+" , outPath="+pDateipfadProcessed;
			}
			if(!dirto.isDirectory()) {
				return "no dir:"+" outPath="+pDateipfadProcessed;
			}
			if(dir.getAbsolutePath().equals(dirto.getAbsolutePath())) {
				return "must be different:"+" inPath="+pDateipfad+" , outPath="+pDateipfadProcessed;
			}
			for(int i=0; i<files.length; i++) {
				String uri = dir.getAbsolutePath() + File.separator + files[i];
				File file = new File(uri);
				File fileto = new File(dirto.getAbsolutePath() + File.separator + files[i]);
				log.info( "doIt(): use {} : isFile={} fileto.exists={}", uri, file.isFile(), fileto.exists());
				if(file.isFile()) {
					try {
						msg = msg + "<br/>" + doOne(msg, uri);
						boolean moved = this.movefile(file, fileto);
						log.warn("doIt(): moved={} to {}", moved, fileto.getAbsolutePath());
					} catch (Exception e) {
						log.warn(e.getMessage());
						msg = msg + "<br/>" + e.getMessage();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.warn(e.getMessage());
			msg = msg + "<br/>" + e.getMessage();
		}
		return msg;
	}

	protected boolean movefile(File src, File tgt) {
		return src.renameTo(tgt);
	}
	
	private static XmlReader reader = null;
	
	protected XmlReader getXmlReader() {
		if(reader==null) {
			reader = XmlReader.newInstance();
		}
		return reader;
	}
	
	/**
	 * mit unmarshal+doOne wird ein opentrans-XML-Dokument (ORDER) uri 
	 * in entsprechende ADempiere-Objekte konvertiert
	 * 
	 * Der Prozess besteht aus zwei Schritten:
	 *  1. XML-unmarshall lierfert opentrans-pojo ORDER-Objektnetz
	 *  2. mapping liefert ADempiere-Objekte, in doOne()
	 *  
	 * @see https://groups.google.com/forum/#!topic/idempiere/PDA5GU5kxGo
	 * 
	 * @param uri
	 * @return opentrans-pojo ORDER-Objektnetz
	 */
	protected ORDER unmarshal(String uri) {
		XmlReader reader = getXmlReader();
		ORDER order = null;
		try {
			Document doc = reader.read(uri);
			Node o = reader.getOrder(doc);
			order = (ORDER)reader.unmarshal(o, ORDER.class);
		} catch (Exception e) {
			log.warn("unmarshal() : {}", e.getMessage());
			throw new AdempiereException("NO opentrans-ORDER in "+uri );
		}
		return order;
	}
	
	protected String doOne(String msg, String uri) throws Exception {
		
		String ret = uri;
		ORDER order = unmarshal(uri);
		
		// map Products:
		try {
			List<ORDERITEM> otItems = order.getORDERITEMLIST().getORDERITEM();
			int newProducts = 0;
			for(Iterator<ORDERITEM> i=otItems.iterator(); i.hasNext(); ) {
				ORDERITEM item = i.next();
				if(createProductIfNew(item, pDropShipBPartner_ID))
					newProducts++;
			}
			if(newProducts>0)
				ret = ""+newProducts+ " new Product(s) in ORDERID="+order.getORDERHEADER().getORDERINFO().getORDERID();
		} catch (Exception e) {
			log.warn("doOne() Exception: {}", e.getMessage());
			throw new AdempiereException(e.getMessage() + " in "+uri );
		}
		
		return ret;
	}

	private boolean createProductIfNew(ORDERITEM item, int dropShipBPartner_ID) {
		PRODUCTID otProduct = item.getPRODUCTID(); // mandatory
		String vendorProductNo = null;
		MProduct product = null;
		boolean newProduct = true;
		if(otProduct.getSUPPLIERPID()==null) {
			throw new AdempiereException("No SUPPLIERPID" + " in item "+item.getLINEITEMID() );
		} else {
			vendorProductNo = otProduct.getSUPPLIERPID().getValue();
		}
		String desc0 = null;
		List<DESCRIPTIONSHORT> descList = otProduct.getDESCRIPTIONSHORT();
		if(descList==null) {
			// exception ? : später, wenn es tatsächlich benötigt wird
		} else {
			if(descList.size()==0) {
				// exception ?
			} else {
				desc0 = descList.get(0).getValue();
			}
		}
		PRODUCTPRICEFIX otPrice = item.getPRODUCTPRICEFIX();
		
		List<MProduct> pl = getProduct(vendorProductNo, dropShipBPartner_ID);		
		if(pl.isEmpty()) {
			log.info("createProductIfNew: *new Product* VendorProductNo={}, desc0={}", vendorProductNo, desc0);
			BigDecimal pricepp = null;
			BigDecimal tax = null;
			if(otPrice==null) {
				throw new AdempiereException("No PRODUCTPRICE" + " in item "+item.getLINEITEMID() );
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
			
			//log.info("createProductIfNew: new MProduct(this.getCtx(), 0, TrxName={})", this.get_TrxName());
			product = new MProduct(this.getCtx(), 0, this.get_TrxName());
			
			product.setSKU("::"+vendorProductNo);
			log.info("createProductIfNew: SKU={})", product.getSKU());
			
			if(desc0==null) {
				throw new AdempiereException("No DESCRIPTION" + " in item "+item.getLINEITEMID() );
			} else {
				product.setName(desc0);	
			}
			
			// wg. FEHLER: NULL-Wert in Spalte „m_product_category_id“ verletzt Not-Null-Constraint
			product.setM_Product_Category_ID(product.getDefaultProductCategory().getM_Product_Category_ID());

			// wg. FEHLER: NULL-Wert in Spalte „c_uom_id“ verletzt Not-Null-Constraint
			MUOM unit = MUoM.getOrCreate(this.getCtx(), item.getORDERUNIT(), this.get_TrxName());
			product.setC_UOM_ID(unit.getC_UOM_ID());
			
			product.setIsDropShip(MOrder.ISDROPSHIP);
			product.setIsStocked(false); // alle SOE-Produkte werden als "nicht lagerhaltig" definiert 
			
			// mierp-Besonderheit (diese Cols gibt es in metasfresh nicht):
//			product.set_ValueOfColumnReturningBoolean(MProduct.COLUMNNAME_priceso, pricepp);
//			product.set_ValueOfColumnReturningBoolean(MProduct.COLUMNNAME_vendor_id, dropShipBPartner_ID);
			product.saveEx(this.get_TrxName());
			
			// pPO: auf mierp darf es nur einen geben!
			MProductPO pPO = product.findOrCreateMProductPO(dropShipBPartner_ID, vendorProductNo);
			pPO.setPriceList(pricepp);
			pPO.setC_UOM_ID(unit.getC_UOM_ID());
			pPO.saveEx(this.get_TrxName());
			
			// MProductPrice erstellen (vorsichtshalber existierende beachten, siehe com.klst.*.process.ImportProduct) 
			log.info("createProductIfNew: PRICEAMOUNT={} PRICEQUANTITY={} pricepp={}", otPrice.getPRICEAMOUNT(), otPrice.getPRICEQUANTITY(), pricepp);
			MPriceListVersion plv = MPriceListVersion.getDefaultSOPriceListVersion(getCtx(), get_TrxName());
			if(plv==null) {
				log.error("createProductIfNew: No MPriceListVersion");
				throw new AdempiereException("cannot find a DefaultSOPriceList SOE");
			} else {
				log.info("createProductIfNew: plv.M_PriceList_Version_ID={}",plv.getM_PriceList_Version_ID());
			}
			int plvID = plv.getM_PriceList_Version_ID();
			
			// metasfresh: MProductPrice.get is deprecated -> changed to Query API
			Optional<I_M_ProductPrice> pp = ProductPriceQuery.retrieveMainProductPriceIfExists(plv, product.getM_Product_ID());
			I_M_ProductPrice price = null;
			if(pp.isPresent()) {
				price = pp.get();
			} else {
				price = new MProductPrice(getCtx(), plvID, product.getM_Product_ID(), get_TrxName());
			}
			// wg. FEHLER: NULL-Wert in Spalte „c_uom_id“ verletzt Not-Null-Constraint
			if(price.getC_UOM() == null) {
				price.setC_UOM_ID(unit.getC_UOM_ID());
			}

			// in mf ist das DM anders: tax hängt an m_productprice.c_taxcategory_id und liefert
			// wg. FEHLER: Einfügen oder Aktualisieren in Tabelle „m_productprice“ verletzt Fremdschlüssel-Constraint „ctaxcategory_mproductprice“ 
			//     Detail: Schlüssel (c_taxcategory_id)=(0) ist nicht in Tabelle „c_taxcategory“ vorhanden.
			if(tax==null) {
				price.setC_TaxCategory_ID(product.getDefaultTaxCategory().getC_TaxCategory_ID());
			} else {
				price.setC_TaxCategory_ID(product.getTaxCategory(tax).getC_TaxCategory_ID());
			}
			
			//price.setPrices(pricepp, pricepp, pricepp); // (PriceList, PriceStd, PriceLimit)
			price.setPriceLimit(pricepp);
			price.setPriceList(pricepp);
			price.setPriceStd(pricepp);
			// auf mierp c_taxcategory_id in TABLE m_productprice 
			((MProductPrice)InterfaceWrapperHelper.getPO(price)).saveEx(this.get_TrxName());
			
			log.info("createProductIfNew: product="+product + " pPO="+pPO + " price="+price);
		} else {
			product = pl.get(0); 
			newProduct = false;
		}
		return newProduct;
	}

	private static final String SQL_PRODUCT_PO = "SELECT m_product_id FROM m_product_po"
			+ " WHERE isactive='Y' AND c_bpartner_id = ? ";
	private static final String SQL_PRODUCT = "SELECT m_product_id FROM m_product"
			+ " WHERE isactive='Y' AND sku like ? and m_product_id IN(" + SQL_PRODUCT_PO + ")";
	private PreparedStatement pstmtProduct; // sucht ein Produkt
	
	/* zum Finden hat man nur SUPPLIERPID / in SKU hinter '::' , Bsp PGI525BK::101748090 
	 * sollte genau ein Produkt liefern, oder nix. Bei mehr bleibt nur exception über
	 * 
	 * not unique! Product '337014401' result.size=2 in C:\proj\min*\input\order_LS3_31234_8659_2014-10-10-.013
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
			log.info("getProduct(otProductSupplierPid={} , dropShipBPartner_ID={}) loop through ResultSet sql \n {}"
					, otProductSupplierPid, dropShipBPartner_ID, SQL_PRODUCT);
			while (rs.next()) {
				M_Product_ID = rs.getInt(1);
				log.info("getProduct: M_Product_ID={} otProductSupplierPid={}", M_Product_ID, otProductSupplierPid);
				resultList.add(new MProduct(this.getCtx(), M_Product_ID, this.get_TrxName()));
			}
			if(resultList.size()>1) {
				// hier genuegt eine Warnung : den Fehler gibt es aber in CreateOrderProcess
//				throw new AdempiereException(" not unique! Product '" + otProductSupplierPid + "' result.size="+resultList.size());
				log.warn("getProduct: not unique! Product '{}' result.size=", otProductSupplierPid, resultList.size());
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
