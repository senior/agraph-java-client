/*

 */
package org.openrdf.repository.sail;

import info.aduna.iteration.CloseableIteration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openrdf.OpenRDFUtil;
import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Namespace;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.NamespaceImpl;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.parser.ParsedBooleanQuery;
import org.openrdf.query.parser.ParsedGraphQuery;
import org.openrdf.query.parser.QueryParserUtil;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.base.RepositoryConnectionBase;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.sail.SailConnection;
import org.openrdf.sail.SailException;

import franz.exceptions.SoftException;
import franz.exceptions.UnimplementedMethodException;

/**
 * An implementation of the {@link RepositoryConnection} interface that wraps a
 * {@link SailConnection}.
 * 
 * @author jeen
 * @author Arjohn Kampman
 */
public class AllegroRepositoryConnection extends RepositoryConnectionBase {

	protected static Set<URI> ALL_CONTEXTS = new HashSet<URI>(); 
	protected static String MINI_NULL_CONTEXT = "null";
	
	/**
	 * Constructor.
	 * Creates a new repository connection that will wrap the supplied
	 * SailConnection. SailRepositoryConnection objects are created by
	 * {@link SailRepository#getConnection}.
	 */
	protected AllegroRepositoryConnection(AllegroRepository repository) {
		super(repository);
	}	

	
	//------------------------------------------------------------------------------------
	// Helper methods
	//------------------------------------------------------------------------------------

	protected miniclient.Repository getMiniRepository() {
		return ((AllegroRepository)this.getRepository()).getMiniRepository();
	}
	
	/**
	 * If 'term' is an OpenRDF term, convert it to a string.  If its already
     * a string; assume its in ntriples format, and just pass it through.
	 */
	private String toNtriples(Object term) {
		if (term == null) return (String)term;
		else if (term instanceof String) return (String)term;
		else if (term instanceof URI) return "<" + ((URI)term).toString() + ">";
		else if (term instanceof Literal) return term.toString();
		else if (term instanceof BNode) return term.toString();
		else throw new SoftException("Unexpected datatype passed to 'toNTriples' " + term);
	}
	
	private String contextToNtriples(Resource context, boolean noneIsMiniNull) {
        //if context == MINI_NULL_CONTEXT: return MINI_NULL_CONTEXT
        if (context != null) return this.toNtriples(context);
        else if (noneIsMiniNull) return MINI_NULL_CONTEXT;
        else return null;     
	}
       
    /**
     * Do three transformations here.  Convert from context object(s) to
     * context strings (angle brackets).
     * Also, convert singleton context to list of contexts, and convert
     * ALL_CONTEXTS to None.
     * And, convert None context to 'null'.
     */
    protected List<String> contextsToNtripleContexts(Object contexts, boolean noneIsMiniNull) {
    	List<String> cxts = new ArrayList<String>();
        if (contexts == ALL_CONTEXTS) {
            // consistency would dictate that  null => [null], but this would
            // likely surprise users, so we don't do that:
            cxts = null;
        } else if (contexts == null) {
            if (noneIsMiniNull) cxts.add(MINI_NULL_CONTEXT);
            else cxts = null;
        } else if (contexts instanceof Resource[]) {
        	if ((((Resource[])contexts).length == 0) && noneIsMiniNull) {
        		cxts.add(MINI_NULL_CONTEXT);
        	} else {
        		for (Resource c : (Resource[])contexts) {
        			cxts.add(this.contextToNtriples(c, true));
        		}
        	}
        } else if (contexts instanceof List) {
        	for (URI c : (List<URI>)contexts) {
        		cxts.add(this.contextToNtriples(c, true));   
        	}
    	} else {
    		cxts.add(this.contextToNtriples((Resource)contexts, true));
    	}
        return cxts;
	}

	//------------------------------------------------------------------------------------
	// API Methods
	//------------------------------------------------------------------------------------

	public ValueFactory getValueFactory() {
		return this.getRepository().getValueFactory();
	}

	/**
	 * Returns the underlying SailConnection.
	 */
	public SailConnection getSailConnection() {
		throw new UnimplementedMethodException("getSailConnection");
	}

	public void commit() {
	}

	public void rollback() {
		throw new UnimplementedMethodException("rollback");
	}


	// currently, there is nothing to close in AllegroGraph	
//	public void close(){
//	}

	public TupleQuery prepareQuery(QueryLanguage ql, String queryString, String baseURI) {
		throw new UnimplementedMethodException("prepareQuery");
	}

	/**
	 * Embed 'queryString' into a query object which can be
     * executed against the RDF storage.  'queryString' must be a SELECT
     * query.  The result of query
     * execution is an iterator of tuples.
	 */
	public AllegroTupleQuery prepareTupleQuery(QueryLanguage ql, String queryString, String baseURI) {
		AllegroTupleQuery query = new AllegroTupleQuery(ql, queryString, baseURI);
		query.setConnection(this);
        return query;
	}

	public SailGraphQuery prepareGraphQuery(QueryLanguage ql, String queryString, String baseURI){
		throw new UnimplementedMethodException("prepareGraphQuery");
	}

	public SailBooleanQuery prepareBooleanQuery(QueryLanguage ql, String queryString, String baseURI){
		throw new UnimplementedMethodException("prepareBooleanQuery");
	}

	public RepositoryResult<Resource> getContextIDs() {
		throw new UnimplementedMethodException("getContextIDs");
	}

	public RepositoryResult<Statement> getStatements(Resource subject, URI predicate, Value object,
			boolean includeInferred, Resource... contexts) {
		Object callback = null;
		AllegroRepository rep = (AllegroRepository)this.getRepository();
		AllegroValueFactory factory = (AllegroValueFactory)rep.getValueFactory(); 
        object = factory.objectPositionTermToOpenRDFTerm(object, predicate);
        List<List<String>>stringTuples = this.getMiniRepository().getStatements(this.toNtriples(subject), this.toNtriples(predicate),
        		this.toNtriples(object), this.contextsToNtripleContexts(contexts, false), false, callback);
        return new AllegroRepositoryResult(stringTuples);
	}

	public void exportStatements(Resource subj, URI pred, Value obj, boolean includeInferred,
			RDFHandler handler, Resource... contexts)
		throws RepositoryException, RDFHandlerException
	{
		handler.startRDF();

		// Export namespace information
		CloseableIteration<? extends Namespace, RepositoryException> nsIter = getNamespaces();
		try {
			while (nsIter.hasNext()) {
				Namespace ns = nsIter.next();
				handler.handleNamespace(ns.getPrefix(), ns.getName());
			}
		}
		finally {
			nsIter.close();
		}

		// Export statements
		CloseableIteration<? extends Statement, RepositoryException> stIter = getStatements(subj, pred, obj,
				includeInferred, contexts);

		try {
			while (stIter.hasNext()) {
				handler.handleStatement(stIter.next());
			}
		}
		finally {
			stIter.close();
		}

		handler.endRDF();
	}

	public long size(Resource... contexts) 	{
		if (contexts.length == 0)
			return this.getMiniRepository().getSize();
		else
			throw new UnimplementedMethodException("size");
	}

	/**
	 * Add the supplied triple of values to this repository, optionally to
     * one or more named contexts.        
	 */
	protected void addWithoutCommit(Resource subject, URI predicate, Value object, Resource... contexts) {
        this.getMiniRepository().addStatement(this.toNtriples(subject), this.toNtriples(predicate),
        		this.toNtriples(object), this.contextsToNtripleContexts(contexts, true));
	}

	@Override
	protected void removeWithoutCommit(Resource subject, URI predicate, Value object, Resource... contexts) {
		List<String> ntripleContexts = this.contextsToNtripleContexts(contexts, true);
		this.getMiniRepository().deleteMatchingStatements(this.toNtriples(subject), this.toNtriples(predicate),
        		this.toNtriples(object), (contexts != null) ? ntripleContexts : null);
	}

	@Override
	public void clear(Resource... contexts) {
		throw new UnimplementedMethodException("clear");
	}
	
	//----------------------------------------------------------------------------------------------
	//  Namespaces
	//----------------------------------------------------------------------------------------------
	
	private Map<String, Namespace> namespaceMap = new HashMap<String, Namespace>();

	public void setNamespace(String prefix, String name) {
		this.namespaceMap.put(prefix, new NamespaceImpl(prefix, name));
	}

	public void removeNamespace(String prefix) {
		this.namespaceMap.remove(prefix);
	}

	public void clearNamespaces() {
		this.namespaceMap.clear();
	}

	public RepositoryResult<Namespace> getNamespaces() {		
		return createRepositoryResult(new ListIterator(this.namespaceMap.values()));
	}

	public String getNamespace(String prefix) {
		Namespace ns = this.namespaceMap.get(prefix);
		return (ns != null) ? ns.getName() : null;
	}

	//----------------------------------------------------------------------------------------------
	//  Namespaces
	//----------------------------------------------------------------------------------------------
	
	public static class  ListIterator<X> implements CloseableIteration<X, Exception> {
		private int cursor = 0;
		private List items = new ArrayList();
		
		public ListIterator (Collection items) {
			this.items.addAll(items);
		}
		
		public void close () {}
		
		public boolean hasNext() { return this.cursor < this.items.size(); }
		
		public X next() {
			if (this.hasNext()) {
				X value = (X)this.items.get(this.cursor);
				this.cursor++;
				return value;
			} else {
				return null;
			}
		}
		
		public void remove () {throw new UnimplementedMethodException("remove");}
	}
	

	/**
	 * Wraps a CloseableIteration coming from a Sail in a RepositoryResult
	 * object, applying the required conversions
	 */
	protected <E> RepositoryResult<E> createRepositoryResult(
			CloseableIteration<? extends E, SailException> sailIter)
	{
		return new RepositoryResult<E>(new SailCloseableIteration<E>(sailIter));
	}
}