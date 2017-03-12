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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;


/*
 * wird nicht von JavaProcess abgeleitet, da mit CreateProductProcess vieles gemeinsam genutzt wird
 * die Funktionalität ist nicht in doOne(), sondern in movefile(...)
 * 
 * prepare() und doIt() aus super!
 */
public class AvisPrepareProcess extends CreateProductProcess {

	@Override
	protected String doOne(String msg, String uri) throws Exception {
		
		String ret = uri;
		log.info("nix tun uri={}", uri );
		return ret;
	}

	@Override
	protected boolean movefile(File src, File tgt) {
		try {
			AvisPipedInputStream is = new AvisPipedInputStream(src.getAbsolutePath(), null); // charsetName=null wg. #343
			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(tgt), Charset.forName("UTF-8"));
			int c = is.read();
			do {
				fw.write(c);
				c = is.read();
			} while(c!=-1);
			fw.close();
			is.close();
			//return true; // derzeit macht die Methode eine Kopie, also kein move!
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

}
