package org.aksw.facete.v3.impl;

import com.google.common.collect.ImmutableList;
import org.aksw.facete.v3.api.*;
import org.aksw.facete.v3.bgp.api.BgpDirNode;
import org.aksw.facete.v3.bgp.api.BgpNode;
import org.aksw.jena_sparql_api.concepts.*;
import org.aksw.jena_sparql_api.utils.ElementUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.expr.E_IsBlank;
import org.apache.jena.sparql.expr.E_LogicalNot;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.Template;
import org.hobbit.benchmark.faceted_browsing.v2.domain.Vocab;
import org.hobbit.benchmark.faceted_browsing.v2.main.FacetedQueryGenerator;

import java.util.Map;

public class FacetDirNodeImpl
	implements FacetDirNode
{
//	abstract boolean isFwd();
//
//	@Override
//	public FacetDirNode getParent() {
//		// TODO Auto-generated method stub
//		return null;
//	}

	protected FacetNodeResource parent;

	protected BgpDirNode state;
	
	//protected boolean isFwd;
	//protected Var alias;
	
	public FacetDirNodeImpl(FacetNodeResource parent, BgpDirNode state) {//boolean isFwd) {
		this.parent = parent;
		//this.isFwd = isFwd;
		this.state = state;
	}
	
	@Override
	public FacetNodeResource parent() {
		return parent;
	}

	@Override
	public FacetMultiNode via(Resource property) {
		return new FacetMultiNodeImpl(parent, state.via(property));		
		//return new FacetMultiNodeImpl(parent, property, isFwd);
	}
	
	@Override
	public DataQuery<RDFNode> facets() {
		FacetedQueryResource facetedQuery = this.parent().query();
		
		BgpNode bgpRoot = facetedQuery.modelRoot().getBgpRoot();

//		BinaryRelation br = FacetedBrowsingSessionImpl.createQueryFacetsAndCounts(path, isReverse, pConstraint);
		FacetedQueryGenerator<BgpNode> qgen = new FacetedQueryGenerator<>(new PathAccessorImpl(bgpRoot));
		facetedQuery.modelRoot().constraints().forEach(c -> qgen.addConstraint(c.expr()));

		UnaryRelation concept = qgen.createConceptFacets(parent.state(), !this.state.isFwd(), false, null);
		
//		BinaryRelation br = FacetedQueryGenerator.createRelationFacetsAndCounts(relations, pConstraint)(relations, null);
//
//		
//		BasicPattern bgp = new BasicPattern();
//		bgp.add(new Triple(br.getSourceVar(), Vocab.facetCount.asNode(), br.getTargetVar()));
//		Template template = new Template(bgp);
//		
		DataQuery<RDFNode> result = new DataQueryImpl<>(parent.query().connection(), concept.getVar(), concept.getElement(), null, RDFNode.class);
//
		return result;
	}
	
	@Override
	public DataQuery<FacetCount> facetCounts() {
		FacetedQueryResource facetedQuery = this.parent().query();
		BgpNode bgpRoot = facetedQuery.modelRoot().getBgpRoot();
		
//		BinaryRelation br = FacetedBrowsingSessionImpl.createQueryFacetsAndCounts(path, isReverse, pConstraint);
		FacetedQueryGenerator<BgpNode> qgen = new FacetedQueryGenerator<>(new PathAccessorImpl(bgpRoot));
		facetedQuery.constraints().forEach(c -> qgen.addConstraint(c.expr()));

		Map<String, BinaryRelation> relations = qgen.createMapFacetsAndValues(parent.state(), !this.state.isFwd(), false);
		
		BinaryRelation br = FacetedQueryGenerator.createRelationFacetsAndCounts(relations, null);
		
		
		BasicPattern bgp = new BasicPattern();
		bgp.add(new Triple(br.getSourceVar(), Vocab.facetCount.asNode(), br.getTargetVar()));
		Template template = new Template(bgp);
		
		DataQuery<FacetCount> result = new DataQueryImpl<>(parent.query().connection(), br.getSourceVar(), br.getElement(), template, FacetCount.class);

		return result;
	}

	@Override
	public DataQuery<FacetValueCount> facetValueCounts() {
		DataQuery<FacetValueCount> result = createQueryFacetValueCounts(false);
		return result;
//		FacetedQueryResource facetedQuery = this.parent().query();
//
////		BinaryRelation br = FacetedBrowsingSessionImpl.createQueryFacetsAndCounts(path, isReverse, pConstraint);
//		FacetedQueryGenerator<BgpNode> qgen = new FacetedQueryGenerator<>(new PathAccessorImpl(facetedQuery.modelRoot().getBgpRoot()));
//		facetedQuery.constraints().forEach(c -> qgen.addConstraint(c.expr()));
//
//		TernaryRelation tr = qgen.createRelationFacetValues(this.parent().query().focus().state(), this.parent().state(), !this.state.isFwd(), false, null, null);
//		
//		// Inject that the object must not be a blank node
//		// TODO There should be a better place to do this - but where?		
//		tr = new TernaryRelationImpl(ElementUtils.createElementGroup(ImmutableList.<Element>builder()
//				.addAll(tr.getElements())
//				.add(new ElementFilter(new E_LogicalNot(new E_IsBlank(new ExprVar(tr.getP())))))
//				.build()),
//				tr.getS(),
//				tr.getP(),
//				tr.getO());
//		
//
//		
//		
//		
//		BasicPattern bgp = new BasicPattern();
//		bgp.add(new Triple(tr.getS(), Vocab.value.asNode(), tr.getP()));
//		bgp.add(new Triple(tr.getS(), Vocab.facetCount.asNode(), tr.getO()));
//		Template template = new Template(bgp);
//		
//		DataQuery<FacetValueCount> result = new DataQueryImpl<>(parent.query().connection(), tr.getS(), tr.getElement(), template, FacetValueCount.class);
//
//		return result;
	}


	@Override
	public FacetedQuery getQuery() {
		// TODO Auto-generated method stub
		throw new NotImplementedException();
	}

	@Override
	public BinaryRelation facetValueRelation() {
		FacetedQueryResource facetedQuery = this.parent().query();

		FacetedQueryGenerator<BgpNode> qgen = new FacetedQueryGenerator<>(new PathAccessorImpl(facetedQuery.modelRoot().getBgpRoot()));
		facetedQuery.constraints().forEach(c -> qgen.addConstraint(c.expr()));

		TernaryRelation tr = qgen.createRelationFacetValue(this.parent().query().focus().state(), this.parent().state(), !this.state.isFwd(), null, null, false);

		BinaryRelation result = new BinaryRelationImpl(tr.getElement(), tr.getP(), tr.getO());
		return result;
	}


	/**
	 * Common method for (non-)negated facet value counts
	 * 
	 * @param negated
	 * @return
	 */
	public DataQuery<FacetValueCount> createQueryFacetValueCounts(boolean negated) {
		FacetedQueryResource facetedQuery = this.parent().query();

//		BinaryRelation br = FacetedBrowsingSessionImpl.createQueryFacetsAndCounts(path, isReverse, pConstraint);
		FacetedQueryGenerator<BgpNode> qgen = new FacetedQueryGenerator<>(new PathAccessorImpl(facetedQuery.modelRoot().getBgpRoot()));
		facetedQuery.constraints().forEach(c -> qgen.addConstraint(c.expr()));

		TernaryRelation tr = qgen.createRelationFacetValues(this.parent().query().focus().state(), this.parent().state(), !this.state.isFwd(), negated, null, null);
		
		// Inject that the object must not be a blank node
		// TODO There should be a better place to do this - but where?		
		tr = new TernaryRelationImpl(ElementUtils.createElementGroup(ImmutableList.<Element>builder()
				.addAll(tr.getElements())
				.add(new ElementFilter(new E_LogicalNot(new E_IsBlank(new ExprVar(tr.getP())))))
				.build()),
				tr.getS(),
				tr.getP(),
				tr.getO());
		

		
		
		
		BasicPattern bgp = new BasicPattern();
		bgp.add(new Triple(tr.getS(), Vocab.value.asNode(), tr.getP()));
		bgp.add(new Triple(tr.getS(), Vocab.facetCount.asNode(), tr.getO()));
		Template template = new Template(bgp);
		
		DataQuery<FacetValueCount> result = new DataQueryImpl<>(parent.query().connection(), tr.getS(), tr.getElement(), template, FacetValueCount.class);

		return result;
	}

	@Override
	public DataQuery<FacetValueCount> nonConstrainedFacetValueCounts() {
		DataQuery<FacetValueCount> result = createQueryFacetValueCounts(true);
		return result;
	}

//	@Override
//	public ExprFragment2 constraintExpr() {
//		FacetedQueryResource facetedQuery = this.parent().query();
//
//		FacetedQueryGenerator<BgpNode> qgen = new FacetedQueryGenerator<>(new PathAccessorImpl(facetedQuery.modelRoot().getBgpRoot()));
//		facetedQuery.constraints().forEach(c -> qgen.addConstraint(c.expr()));
//	
//		// TODO Get the constraint expression on that path
//		qgen.getConceptForAtPath(focusPath, facetPath, applySelfConstraints);
//
//		xxx;
//	}
	
}
