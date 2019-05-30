package org.aksw.jena_sparql_api.data_query.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.commons.collections.trees.TreeUtils;
import org.aksw.jena_sparql_api.algebra.transform.TransformDeduplicatePatterns;
import org.aksw.jena_sparql_api.algebra.transform.TransformFilterFalseToEmptyTable;
import org.aksw.jena_sparql_api.algebra.transform.TransformFilterSimplify;
import org.aksw.jena_sparql_api.algebra.transform.TransformPromoteTableEmptyVarPreserving;
import org.aksw.jena_sparql_api.algebra.transform.TransformPullFiltersIfCanMergeBGPs;
import org.aksw.jena_sparql_api.algebra.transform.TransformPushFiltersIntoBGP;
import org.aksw.jena_sparql_api.algebra.transform.TransformRedundantFilterRemoval;
import org.aksw.jena_sparql_api.algebra.utils.FixpointIteration;
import org.aksw.jena_sparql_api.beans.model.EntityModel;
import org.aksw.jena_sparql_api.beans.model.EntityOps;
import org.aksw.jena_sparql_api.beans.model.PropertyOps;
import org.aksw.jena_sparql_api.concepts.Concept;
import org.aksw.jena_sparql_api.concepts.Relation;
import org.aksw.jena_sparql_api.concepts.RelationImpl;
import org.aksw.jena_sparql_api.concepts.UnaryRelation;
import org.aksw.jena_sparql_api.core.utils.ReactiveSparqlUtils;
import org.aksw.jena_sparql_api.data_query.api.DataMultiNode;
import org.aksw.jena_sparql_api.data_query.api.DataNode;
import org.aksw.jena_sparql_api.data_query.api.DataQuery;
import org.aksw.jena_sparql_api.data_query.api.PathAccessor;
import org.aksw.jena_sparql_api.data_query.api.SPath;
import org.aksw.jena_sparql_api.mapper.impl.type.RdfTypeFactoryImpl;
import org.aksw.jena_sparql_api.utils.CountInfo;
import org.aksw.jena_sparql_api.utils.ElementUtils;
import org.aksw.jena_sparql_api.utils.Generator;
import org.aksw.jena_sparql_api.utils.QueryUtils;
import org.aksw.jena_sparql_api.utils.VarGeneratorBlacklist;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.ARQ;
import org.apache.jena.query.Query;
import org.apache.jena.query.SortCondition;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdfconnection.SparqlQueryConnection;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.Transformer;
import org.apache.jena.sparql.algebra.optimize.Optimize;
import org.apache.jena.sparql.algebra.optimize.Rewrite;
import org.apache.jena.sparql.algebra.optimize.RewriteFactory;
import org.apache.jena.sparql.algebra.optimize.TransformMergeBGPs;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.Rename;
import org.apache.jena.sparql.expr.E_Random;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.aggregate.AggSample;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementSubQuery;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.jena.sparql.syntax.PatternVars;
import org.apache.jena.sparql.syntax.Template;
import org.apache.jena.sparql.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jsonldjava.shaded.com.google.common.collect.Maps;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class DataQueryImpl<T extends RDFNode>
	implements DataQuery<T>
{
	// TODO Actually, there should be no logger here - instead there
	// should be some peekQuery(Consumer<Query>) if one wants to know the query
	private static final Logger logger = LoggerFactory.getLogger(DataQueryImpl.class);

	
	protected SparqlQueryConnection conn;
	
//	protected Node rootVar;
//	protected Element baseQueryPattern;

	
	// FIXME for generalization, probably this attribute has to be replaced by
	// a something similar to a list of roots; ege DataNode
	protected Relation baseRelation;
	
	protected Template template;
	
	protected List<DataNode> dataNodes;
	
//	protected Range<Long> range;

	protected Long limit;
	protected Long offset;
	
	protected UnaryRelation filter;
	
	protected List<Element> directFilters = new ArrayList<>();
	
	protected boolean ordered;
	protected boolean randomOrder;
	protected boolean sample;
	
	
	protected Random pseudoRandom = null;

	
	protected Class<T> resultClass;

	protected List<SortCondition> sortConditions;
	
	public DataQueryImpl(SparqlQueryConnection conn, Node rootNode, Element baseQueryPattern, Template template, Class<T> resultClass) {
		this(conn, new Concept(baseQueryPattern, (Var)rootNode), template, resultClass);
	}

	public DataQueryImpl(SparqlQueryConnection conn, Relation baseRelation, Template template, Class<T> resultClass) {
		super();
		this.conn = conn;
//		this.rootVar = rootNode;
//		this.baseQueryPattern = baseQueryPattern;
		this.baseRelation = baseRelation;
		this.template = template;
		this.resultClass = resultClass;
	}

	@Override
	public SparqlQueryConnection connection() {
		return conn;
	}
	
	@Override
	public DataQuery<T> connection(SparqlQueryConnection connection) {
		this.conn = connection;
		return this;
	}
	
	public <U extends RDFNode> DataQuery<U> as(Class<U> clazz) {
		return new DataQueryImpl<U>(conn, baseRelation, template, clazz);
	}
	
	@Override
	public DataQuery<T> limit(Long limit) {
		this.limit = limit;
		return this;
	}

	@Override
	public DataQuery<T> offset(Long offset) {
		this.offset = offset;
		return this;
	}
	
	@Override
	public DataQuery<T> sample(boolean onOrOff) {
		this.sample = onOrOff;
		return this;
	}
	
	@Override
	public boolean isSampled() {
		return sample;
	}

	
	@Override
	public DataQuery<T> ordered(boolean onOrOff) {
		this.ordered = onOrOff;
		return this;
	}
	
	@Override
	public boolean isOrdered() {
		return ordered;
	}

	@Override
	public boolean isRandomOrder() {
		return randomOrder;
	}

	@Override
	public DataQuery<T> randomOrder(boolean onOrOff) {
		this.randomOrder = onOrOff;
		return this;
//		return this;
	}

	//protected void setOffset(10);


	/**
	 * Setting a random number generator (rng) makes query execution deterministic:
	 * Random effects on result sets will be processed in the client:
	 * Randomly ordered result sets will be fully loaded into the client and shuffeled in respect
	 * to the given rng.
	 * 
	 * TODO We may need extra an extra 'deterministic' attribute to indicate
	 * whether to sort result sets - the problem is, that the same query on data loaded at different
	 * times may yield different results. For practical purposes it rarely happens that the same query
	 * yields different results if there were no changes in the data. But maybe it could happen
	 * if a DB did something similar to postgres' vacuum process?
	 * 
	 * @param pseudoRandom
	 * @return
	 */
	@Override
	public DataQuery<T> pseudoRandom(Random pseudoRandom) {
		this.pseudoRandom = pseudoRandom;
		return this;
	}
	
	
	@Override
	public Concept fetchPredicates() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public DataNode getRoot() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public DataMultiNode add(Property property) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public DataQuery<T> filter(UnaryRelation concept) {
		if(concept != null) {
			if(filter == null) {
				filter = concept;
			} else {
				filter = filter.joinOn(filter.getVar()).with(concept).toUnaryRelation();
			}
		}

		return this;
	}

	@Override
	public DataQuery<T> filterDirect(Element element) {
		directFilters.add(element);
		
		return this;
	}

	
	
	@Override
	public DataQuery<T> peek(Consumer<? super DataQuery<T>> consumer) {
		consumer.accept(this);
		return this;
	}
	
	@Override
	public DataQuery<T> filterUsing(Relation relation, String... attrNames) {

		if(relation != null) {
			List<Var> vars = Arrays.asList(attrNames).stream()
					.map(this::resolveAttrToVar)
					.collect(Collectors.toList());
	
			baseRelation = baseRelation.joinOn(vars).with(relation);
		}
		return this;
	}

	public Var resolveAttrToVar(String attr) {
		EntityOps entityOps = EntityModel.createDefaultModel(resultClass, null);
		
		PropertyOps pops = entityOps.getProperty(attr);
		String iri = RdfTypeFactoryImpl.createDefault().getIri(entityOps, pops);

		// FIXME HACK We need to start from the root node instead of iterating the flat list of triple patterns
		Triple match = template.getBGP().getList().stream()
			.filter(t -> t.getPredicate().getURI().equals(iri))
			.findFirst()
			.orElse(null);

		Node node = Optional.ofNullable(match.getObject())
				.orElseThrow(() -> new RuntimeException("No member with name " + attr + " in " + resultClass));

		Var result = (Var)node;
		return result;
	}

	@Override
	public NodePath get(String attr) {
		
		Var var = resolveAttrToVar(attr);
//		Node node = Optional.ofNullable(iri)
//				.map(NodeFactory::createURI)				
//				.orElseThrow(() -> new MemberNotFoundException("No member with name " + attr + " in " + resultClass));

//		
//		
//		Node node = Optional.ofNullable(pops)
//			.map(p -> p.findAnnotation(Iri.class))
//			.map(Iri::value)
//			.map(NodeFactory::createURI)
//			.orElseThrow(() -> new MemberNotFoundException("No member with name " + attr + " in " + resultClass));
		
//		System.out.println("Found: " + node);
		
		Model m = ModelFactory.createDefaultModel();
		SPath tmp = new SPathImpl(m.createResource().asNode(), (EnhGraph)m);
		tmp.setAlias(var);
		
//		tmp = tmp.get(node.getURI(), false);
		
		
		//tmp.setAlias(alias);

		NodePath result = new NodePath(tmp);
		
		return result;
	}
	
	
	@Override
	public DataQuery<T> filter(Expr expr) {
		PathAccessor<SPath> pathAccessor = new PathAccessorSPath();
		PathToRelationMapper<SPath> mapper = new PathToRelationMapper<>(pathAccessor, "w");

		Collection<Element> elts = FacetedQueryGenerator.createElementsForExprs(mapper, pathAccessor, Collections.singleton(expr), false);

		// FIXME Hack to obtain a zero-length path; equals on SPath is broken
		SPath root = PathAccessorUtils.getPathsMentioned(expr, pathAccessor::tryMapToPath).values().stream()
			.map(p -> TreeUtils.findRoot(p, pathAccessor::getParent))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Should not happen: Expr without path - " + expr));
		
		
		//SPath root = ModelFactory.createDefaultModel().createResource().as(SPath.class);
		//Var rootVar = (Var)pathAccessor.(root);
		
		if(false) {
		Var rootVar = (Var)mapper.getNode(root);
		
		UnaryRelation tmp = new Concept(ElementUtils.groupIfNeeded(elts), rootVar);
		filter(tmp);
		} else {
			filterDirect(ElementUtils.groupIfNeeded(elts));
		}
		//TreeUtils.getRoot(item, predecessor)
		
		//NodeTransform xform = PathToRelationMapper.createNodeTransformSubstitutePathReferences(new PathAccessorSPath());

		//Expr e = expr.applyNodeTransform(xform);
		// Transformation has to be done using NodeTransformExpr as
		// it correctly substitutes NodeValues with ExprVars
//		Expr e = ExprTransformer.transform(new NodeTransformExpr(xform), expr);
//		
//		UnaryRelation f = DataQuery.toUnaryFiler(e);
//		filter(f);
		
		return this;
	}

	
	
	// Note: The construct template may be empty - use in conjunction with ReactiveSparqlUtils.execPartitioned()
	@Override
	public Entry<Node, Query> toConstructQuery() {
		Set<Var> vars = new LinkedHashSet<>();
		Node rootVar = baseRelation.getVars().get(0);
		if(rootVar.isVariable()) {
			vars.add((Var)rootVar);
		}
		
		//boolean canAsConstruct = template != null && !template.getBGP().isEmpty();

		if(template != null) {
			vars.addAll(PatternVars.vars(new ElementTriplesBlock(template.getBGP())));
		}

		Query query = new Query();
		query.setQuerySelectType();
		for(Var v : vars) {
			query.getProject().add(v);
		}
//		query.setQueryConstructType();
//		query.setConstructTemplate(template);

		
		//query.setQueryResultStar(true);
		//query.setQuerySelectType();

		// Start with a select query, optimize it, and at the end turn it into construct
//		query.setQuerySelectType();

		
		Element baseQueryPattern = baseRelation.getElement();
		
		Element effectivePattern = filter == null
				? baseQueryPattern
				: new RelationImpl(baseQueryPattern, new ArrayList<>(PatternVars.vars(baseQueryPattern))).joinOn((Var)rootVar).with(filter).getElement()
				;

		if(!directFilters.isEmpty()) {
			effectivePattern = ElementUtils.groupIfNeeded(Iterables.concat(Collections.singleton(effectivePattern), directFilters));
		}
		
		
		boolean deterministic = pseudoRandom != null;

		if(sample) {
			Set<Var> allVars = new LinkedHashSet<>();
			allVars.addAll(vars);
			allVars.addAll(PatternVars.vars(baseQueryPattern));
			
			Generator<Var> varGen = VarGeneratorBlacklist.create(allVars);
			Var innerRootVar = varGen.next();
			
//			if(baseQueryPattern instanceof ElementSubQuery) {
//				QueryGroupExecutor.createQueryGroup()
//
//			}
			
			Element innerE = ElementUtils.createRenamedElement(effectivePattern, Collections.singletonMap(rootVar, innerRootVar));

			
			Query inner = new Query();
			inner.setQuerySelectType();
			inner.setQueryPattern(innerE);
			Expr agg = inner.allocAggregate(new AggSample(new ExprVar(innerRootVar)));
			inner.getProject().add((Var)rootVar, agg);
			
			if(!(randomOrder && deterministic)) {
				QueryUtils.applySlice(inner, offset, limit, false);
			}

			Element e = ElementUtils.groupIfNeeded(new ElementSubQuery(inner), effectivePattern);
						
			query.setQueryPattern(e);
		} else {
			
			// TODO Controlling distinct should be possible on this class
			query.setDistinct(true);
	
			query.setQueryPattern(effectivePattern);
			
			if(!(randomOrder && deterministic)) {
				QueryUtils.applySlice(query, offset, limit, false);
			}
		}

		
		
		if(ordered) {
			query.addOrderBy(new ExprVar(rootVar), Query.ORDER_ASCENDING);
		}		

		if(randomOrder && !deterministic) {
			query.addOrderBy(new E_Random(), Query.ORDER_ASCENDING);
//			query.addOrderBy(new E_RandomPseudo(), Query.ORDER_ASCENDING);
		}
		
		
		//logger.info("Generated query: " + query);

		Rewrite rewrite = createDefaultRewriter();
		query = rewrite(query, rewrite::rewrite);

		// NOTE: The CONSTRUCT part gets lost in the rewriting, restore it 
		
		//if(canAsConstruct) {
		//}

		
		Query c = QueryUtils.selectToConstruct(query, template);

//		logger.debug("After rewrite: " + query);


		// Pattern p = Pattern.compile("^.*v_2\\s*<[^>]*>\\s*v_2.*$", Pattern.MULTILINE);
		// if(p.matcher("" + query).find()) {
		// 	System.out.println("DEBUG POINT reached");
		// }
		
		return Maps.immutableEntry((Var)rootVar, c);
	}


	@Override
	public Flowable<T> exec() {
		Objects.requireNonNull(conn);

		Entry<Node, Query> e = toConstructQuery();
//		Node rootVar = e.getKey();
//		Query query = e.getValue();
		
		// PseudoRandomness affects:
		// limit, offset, orderByRandom, ... what else?
		
		
		logger.debug("Executing query:\n" + e);
//		
//		Flowable<T> result = ReactiveSparqlUtils
//			// For future reference: If we get an empty results by using the query object, we probably have wrapped a variable with NodeValue.makeNode. 
//			.execSelect(() -> conn.query(query))
//			.map(b -> {
//				Graph graph = GraphFactory.createDefaultGraph();
//
//				// TODO Re-allocate blank nodes
//				if(template != null) {
//					Iterator<Triple> it = TemplateLib.calcTriples(template.getTriples(), Iterators.singletonIterator(b));
//					while(it.hasNext()) {
//						Triple t = it.next();
//						graph.add(t);
//					}
//				}
//
//				Node rootNode = rootVar.isVariable() ? b.get((Var)rootVar) : rootVar;
//				
//				Model m = ModelFactory.createModelForGraph(graph);
//				RDFNode r = m.asRDFNode(rootNode);
//				
////				Resource r = m.createResource()
////				.addProperty(RDF.predicate, m.asRDFNode(valueNode))
////				.addProperty(Vocab.facetValueCount, );
////			//m.wrapAsResource(valueNode);
////			return r;
//
//				return r;
//			})
		
		Flowable<T> result = ReactiveSparqlUtils.execPartitioned(conn, e)
			.map(r -> r.as(resultClass));
		
		
		boolean deterministic = pseudoRandom != null;

		if(deterministic && randomOrder) {
			result = result.toList().map(l -> {
				// Always sort the collection, so that subsequent shuffle will give the same result
				// regardless of initial order
				Collections.sort(l, (a, b) ->
					NodeValue.compareAlways(NodeValue.makeNode(a.asNode()), NodeValue.makeNode(b.asNode())));

				Collections.shuffle(l, pseudoRandom);
				
				Range<Long> available = Range.closed(0l, (long)l.size());
				Range<Long> requested = QueryUtils.toRange(offset, limit);
				Range<Long> effective = available.intersection(requested);
				long o = effective.lowerEndpoint();
				long size = effective.upperEndpoint() - o;
				
				List<T> subList = l.subList((int)o, (int)size);
				
				return subList;
			}).toFlowable().flatMap(Flowable::fromIterable);
		}
		
		return result;
	}

	@Override
	public Relation baseRelation() {
//		Element effectivePattern = filter == null
//				? baseQueryPattern
//				: new RelationImpl(baseQueryPattern, new ArrayList<>(PatternVars.vars(baseQueryPattern))).joinOn((Var)rootVar).with(filter).getElement()
//				;

		//UnaryRelation result = new Concept(baseQueryPattern, (Var)rootVar);
		return baseRelation;
	}

	@Override
	public Single<Model> execConstruct() {
		return exec().toList().map(l -> {
			Model r = ModelFactory.createDefaultModel();
			for(RDFNode item : l) {
				Model tmp = item.getModel();
				if(tmp != null) {
					r.add(tmp);
				}
			}
			return r;
		});
	}	

	
	// TODO Move to Query Utils
//	public static Query rewrite(Query query, Rewrite rewrite) {
//		Query result = rewrite(query, (Function<? super Op, ? extends Op>)rewrite::rewrite);
//		result.getPrefixMapping().setNsPrefixes(query.getPrefixMapping());
//		return result;
//	}

	// TODO Move to Query Utils
	public static Query rewrite(Query query, Function<? super Op, ? extends Op> rewriter) {
		Op op = Algebra.compile(query);
//System.out.println(op);
		op = rewriter.apply(op);
		
		Query result = OpAsQuery.asQuery(op);
		result.getPrefixMapping().setNsPrefixes(query.getPrefixMapping());
		return result;
	}

	public static Rewrite createDefaultRewriter() {

        Context context = new Context();
        context.put(ARQ.optPromoteTableEmpty, false);
        
        //context.put(ARQ.optReorderBGP, true);
        
        context.put(ARQ.optMergeBGPs, false); // We invoke this manually
        context.put(ARQ.optMergeExtends, true);

        // false; OpAsQuery throws Not implemented: OpTopN (jena 3.8.0)
        context.put(ARQ.optTopNSorting, false);

        context.put(ARQ.optFilterPlacement, true);

//        context.put(ARQ.optFilterPlacement, true);
        context.put(ARQ.optImplicitLeftJoin, false);
        context.put(ARQ.optFilterPlacementBGP, false);
        context.put(ARQ.optFilterPlacementConservative, false); // with false the result looks better

        // Retain E_OneOf expressions
        context.put(ARQ.optFilterExpandOneOf, false);

//        
//
//        // optIndexJoinStrategy mut be off ; it introduces OpConditional nodes which
//        // cannot be transformed back into syntax
        context.put(ARQ.optIndexJoinStrategy, false);
//        
        // It is important to keep optFilterEquality turned off!
        // Otherwise it may push constants back into the quads
        context.put(ARQ.optFilterEquality, false);
        context.put(ARQ.optFilterInequality, false);
        context.put(ARQ.optDistinctToReduced, false);
        context.put(ARQ.optInlineAssignments, false);
        context.put(ARQ.optInlineAssignmentsAggressive, false);
        
        // false; OpAsQuery throws Not implemented: OpDisjunction (jena 3.8.0)
        context.put(ARQ.optFilterDisjunction, false);
        context.put(ARQ.optFilterConjunction, true);
        
        context.put(ARQ.optExprConstantFolding, true);

//        Rewrite rewriter = Optimize.stdOptimizationFactory.create(context);
        RewriteFactory factory = Optimize.getFactory();
        Rewrite core = factory.create(context);
        
        
        // Wrap jena's rewriter with additional transforms
        Rewrite  result = op -> {

        		op = core.rewrite(op);
        		
        		// Issue with Jena 3.8.0 (possibly other versions too)
        		// Jena's rewriter returned by Optimize.getFactory() renames variables (due to scoping)
        		// but does not reverse the renaming - so we need to do it explicitly here
        		// (also, without reversing, variable syntax is invalid, such as "?/0")
        		op = Rename.reverseVarRename(op, true);

        		op = FixpointIteration.apply(op, x -> {
            		x = TransformPullFiltersIfCanMergeBGPs.transform(x);
            		x = Transformer.transform(new TransformMergeBGPs(), x);
            		return x;
        		});

        		op = TransformPushFiltersIntoBGP.transform(op);
        		op = TransformDeduplicatePatterns.transform(op);        		
        		op = TransformRedundantFilterRemoval.transform(op);
        		op = TransformFilterSimplify.transform(op);

        		op = TransformFilterFalseToEmptyTable.transform(op);
        		op = TransformPromoteTableEmptyVarPreserving.transform(op);
        		return op;
        };
        
        return result;
	}

	@Override
	public Single<CountInfo> count() {
		return count(null, null);
	}

	@Override
	public Single<CountInfo> count(Long distinctItemCount, Long rowCount) {
		Entry<Node, Query> e = toConstructQuery();
		Query query = e.getValue();
		//		QueryExecutionUtils.countQuery(query, new QueryExecutionFactorySparqlQueryConnection(conn));
		Single<CountInfo> result = ReactiveSparqlUtils.fetchCountQuery(conn, query, distinctItemCount, rowCount)
				.map(range -> CountUtils.toCountInfo(range));
		
		return result;
	}

	
}
