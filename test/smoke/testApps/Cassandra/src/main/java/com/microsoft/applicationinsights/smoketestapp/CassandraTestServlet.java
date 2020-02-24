package com.microsoft.applicationinsights.smoketestapp;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.base.Stopwatch;

@WebServlet("/*")
public class CassandraTestServlet extends HttpServlet {

    public void init() throws ServletException {
        try {
            setupCassandra();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            doGetInternal(req);
            resp.getWriter().println("ok");
        } catch (ServletException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private void doGetInternal(HttpServletRequest req) throws Exception {
        String pathInfo = req.getPathInfo();
        if (pathInfo.equals("/cassandra")) {
            cassandra();
        } else if (!pathInfo.equals("/")) {
            throw new ServletException("Unexpected url: " + pathInfo);
        }
    }

    private void cassandra() throws Exception {
        Session session = getCassandraSession();
        executeFind(session);
        session.close();
    }

    private static void executeFind(Session session) {
        ResultSet results = session.execute("select * from test.test");
        for (Row row : results) {
            row.getInt("id");
        }
    }

    private static void setupCassandra() throws Exception {
        Session session = getCassandraSession();
        setup(session);
        session.close();
    }

    private static Session getCassandraSession() throws Exception {
        return getCassandraSession(new Callable<Session>() {
            @Override public Session call() {
                String hostname = System.getenv("CASSANDRA");
                Cluster cluster = Cluster.builder()
                        .addContactPointsWithPorts(
                                Arrays.asList(new InetSocketAddress(hostname, 9042)))
                        .build();
                return cluster.connect();
            }
        });
    }

    private static Session getCassandraSession(Callable<Session> callable) throws Exception {
        Exception exception;
        Stopwatch stopwatch = Stopwatch.createStarted();
        do {
            try {
                return callable.call();
            } catch (Exception e) {
                exception = e;
            }
        } while (stopwatch.elapsed(TimeUnit.SECONDS) < 30);
        throw exception;
    }

    private static void setup(Session session) {
        session.execute("CREATE KEYSPACE IF NOT EXISTS test WITH REPLICATION ="
                + " { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }");
        session.execute("CREATE TABLE IF NOT EXISTS test.test (id int PRIMARY KEY, test text)");
        session.execute("TRUNCATE test.test");
        session.execute("INSERT INTO test.test (id, test) VALUES (1, 'test')");
    }
}
