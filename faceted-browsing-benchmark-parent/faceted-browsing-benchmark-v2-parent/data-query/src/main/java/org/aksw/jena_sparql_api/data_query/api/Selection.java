package org.aksw.jena_sparql_api.data_query.api;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.Var;

public interface Selection
	extends Resource
{
//	void setAlias(Var alias);
//	Var getAlias();
	void setAlias(String alias);
	String getAlias();

}