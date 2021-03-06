/******************************************************************************
** Copyright (c) 2008-2010 Franz Inc.
** All rights reserved. This program and the accompanying materials
** are made available under the terms of the Eclipse Public License v1.0
** which accompanies this distribution, and is available at
** http://www.eclipse.org/legal/epl-v10.html
******************************************************************************/

package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static test.Stmt.statementSet;
import static test.Stmt.stmts;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.experimental.categories.Categories;
import org.junit.experimental.categories.Category;
import org.junit.experimental.categories.Categories.ExcludeCategory;
import org.junit.experimental.categories.Categories.IncludeCategory;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;
import org.openrdf.model.BNode;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.query.QueryLanguage;
import org.openrdf.repository.RepositoryResult;

import test.TestSuites.NonPrepushTest;

public class QuickTests extends AGAbstractTest {

    @RunWith(Categories.class)
    @ExcludeCategory(NonPrepushTest.class)
    @SuiteClasses( { QuickTests.class })
    public static class Prepush {}

    @RunWith(Categories.class)
    @IncludeCategory(TestSuites.Broken.class)
    @SuiteClasses( { QuickTests.class })
    public static class Broken {}

    public static final String NS = "http://franz.com/test/";

    @Test
    @Category(TestSuites.Broken.class)
    public void bnode() throws Exception {
        assertEquals("size", 0, conn.size());
        BNode s = vf.createBNode();
        URI p = vf.createURI(NS, "a");
        Literal o = vf.createLiteral("aaa");
        conn.add(s, p, o);
        assertEquals("size", 1, conn.size());
        assertSetsEqual("a", stmts(new Stmt(null, p, o)),
                Stmt.dropSubjects(statementSet(conn.getStatements(s, p, o, true))));
        RepositoryResult<Statement> statements = conn.getStatements(null, null, null, false);
        Statement st = statements.next();
        System.out.println(new Stmt(st));
        AGAbstractTest.assertSetsEqual("",
                Stmt.stmts(new Stmt(st)),
                Stmt.statementSet(conn.getStatements(s, st.getPredicate(), st.getObject(), false)));
        AGAbstractTest.assertSetsEqual("",
                Stmt.stmts(new Stmt(st)),
                Stmt.statementSet(conn.getStatements(st.getSubject(), st.getPredicate(), st.getObject(), false)));
    }

    /**
     * Simplified from tutorial example13 to show the error.
     * Example13 now has a workaround: setting the prefix in the sparql query.
     * Namespaces are cleared in setUp(), otherwise the first errors don't happen.
     * After the (expected) failure for xxx, setting the ont namespace
     * does not hold, so the query with ont fails.
     */
    @Test
    @Category(TestSuites.Broken.class)
    public void namespaceAfterError() throws Exception {
        URI alice = vf.createURI("http://example.org/people/alice");
        URI name = vf.createURI("http://example.org/ontology/name");
        Literal alicesName = vf.createLiteral("Alice");
        conn.add(alice, name, alicesName);
        try {
            conn.prepareBooleanQuery(QueryLanguage.SPARQL,
            "ask { ?s xxx:name \"Alice\" } ").evaluate();
            fail("");
        } catch (Exception e) {
            // expected
            //e.printStackTrace();
        }
        conn.setNamespace("ont", "http://example.org/ontology/");
        assertTrue("Boolean result",
                conn.prepareBooleanQuery(QueryLanguage.SPARQL,
                "ask { ?s ont:name \"Alice\" } ").evaluate());
    }
    
    @Test
    @Category(TestSuites.Temp.class)
    public void bulkDelete() throws Exception {
        URI alice = vf.createURI("http://example.org/people/alice");
        URI firstname = vf.createURI("http://example.org/ontology/firstname");
        URI lastname = vf.createURI("http://example.org/ontology/lastname");
        Literal alicesName = vf.createLiteral("Alice");
        List input = new ArrayList<Statement>();
        input.add(vf.createStatement(alice, firstname, alicesName));
        input.add(vf.createStatement(alice, lastname, alicesName));
        conn.add(input);
        assertEquals("size", 2, conn.size());
        conn.remove(input);
        assertEquals("size", 0, conn.size());
    }

}
