/******************************************************************************
** Copyright (c) 2008-2010 Franz Inc.
** All rights reserved. This program and the accompanying materials
** are made available under the terms of the Eclipse Public License v1.0
** which accompanies this distribution, and is available at
** http://www.eclipse.org/legal/epl-v10.html
******************************************************************************/

package com.franz.agraph.repository;

import java.util.ArrayList;
import java.util.List;

import org.openrdf.OpenRDFException;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.TupleQueryResult;

import com.franz.agraph.http.AGHTTPClient;
import com.franz.agraph.http.AGProtocol;
import com.franz.util.Closeable;
import com.franz.util.Util;

/**
 * The top-level class for interacting with an AllegroGraph server.
 */
public class AGServer implements Closeable {

	private final String serverURL;
	private final AGHTTPClient httpClient;
	private final AGCatalog rootCatalog;
	
	/**
	 * Creates an AGServer instance for interacting with an AllegroGraph
	 * server at serverURL.
	 *     
	 * @param serverURL the URL of the server.
	 * @param username a user id for authenticating with the server 
	 * @param password a password for authenticating with the server  
	 */
	public AGServer(String serverURL, String username, String password) {
		this.serverURL = serverURL;
		httpClient = new AGHTTPClient(serverURL); 
		httpClient.setUsernameAndPassword(username, password);
		rootCatalog = new AGCatalog(this,AGCatalog.ROOT_CATALOG);
	}
	
	/**
	 * Returns the URL of this AllegroGraph server.
	 * 
	 * @return the URL of this AllegroGraph server. 
	 */
	public String getServerURL() {
		return serverURL;
	}
	
	AGHTTPClient getHTTPClient() {
		return httpClient;
	}
	
	/**
	 * Returns the root catalog for this AllegroGraph server.
	 * 
	 * @return the root catalog. 
	 */
	public AGCatalog getRootCatalog() {
		return rootCatalog;
	}
	
	/**
	 * Returns a List of catalog ids known to this AllegroGraph server.
	 * 
	 * @return List of catalog ids.
	 * @throws OpenRDFException
	 */
	public List<String> listCatalogs() throws OpenRDFException {
		String url = AGProtocol.getNamedCatalogsURL(serverURL);
		TupleQueryResult tqresult = getHTTPClient().getTupleQueryResult(url);
		List<String> result = new ArrayList<String>(5);
        try {
            while (tqresult.hasNext()) {
                BindingSet bindingSet = tqresult.next();
                Value id = bindingSet.getValue("id");
                result.add(id.stringValue());
            }
        } finally {
            tqresult.close();
        }
        return result;
	}
	
	/**
	 * Gets the catalog instance for a given catalog id.
	 * 
	 * @param catalogID a catalog id.
	 * @return the corresponding catalog instance.
	 */
	public AGCatalog getCatalog(String catalogID) {
		return new AGCatalog(this, catalogID);
	}

	public AGVirtualRepository virtualRepository(String spec) {
		return new AGVirtualRepository(this, spec, null);
	}

	public AGVirtualRepository federate(AGAbstractRepository... repositories) {
		String[] specstrings = new String[repositories.length];
		for (int i = 0; i < repositories.length; i++)
			specstrings[i] = repositories[i].getSpec();
		String spec = AGVirtualRepository.federatedSpec(specstrings);

		return new AGVirtualRepository(this, spec, null);
	}

    @Override
    public void close() {
        Util.close(httpClient);
    }
	
}
