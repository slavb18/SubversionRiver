package org.elasticsearch.river.subversion;

import com.google.common.collect.Sets;
import org.elasticsearch.river.subversion.crawler.Parameters;
import org.elasticsearch.river.subversion.crawler.SubversionCrawler;
import org.elasticsearch.river.subversion.type.SubversionDocument;
import org.elasticsearch.river.subversion.type.SubversionRevision;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.elasticsearch.river.subversion.crawler.SubversionCrawler.getContent;
import static org.elasticsearch.river.subversion.crawler.SubversionCrawler.getRevisions;


/**
 * Test for SVN SubversionCrawler
 */
public class SubversionCrawlerTest {

    private URL reposAsURL;

    @SuppressWarnings("ConstantConditions")
    @Before
    public void setUp() throws URISyntaxException, MalformedURLException {
        reposAsURL = Thread.currentThread().getContextClassLoader()
                .getResource("TEST_REPOS").toURI().toURL();
    }

    @Test
    public void testGetRevisions() throws SVNException, URISyntaxException {
        List<SubversionRevision> result = getRevisions(
                reposAsURL,
                new Parameters.ParametersBuilder()
                .create()
        );
        int count = 0;
        for(SubversionRevision svnRevision:result) {
            count += svnRevision.getDocuments().size();
        }
        Assert.assertTrue("This repository has normally 8 revisions",result.size() == 8);
        Assert.assertTrue("This repository history has normally 12 documents",count == 12);
    }

    @Test
    public void testGetRevisionsModule1() throws SVNException, URISyntaxException {
        List<SubversionRevision> result = getRevisions(
            reposAsURL,
            new Parameters.ParametersBuilder()
                .setPath("/module1")
            .create()
        );

        Assert.assertTrue("This path features normally 5 revisions",result.size() == 5);
    }

    @Test
    public void testGetContent() throws SVNException, URISyntaxException {
        FSRepositoryFactory.setup();
        SVNRepository repository;
        repository = SVNRepositoryFactory.create(
                SVNURL.fromFile(new File(reposAsURL.toURI()))
        );
        SVNDirEntry entry = repository.info("module1/trunk/watchlist.txt", 7L);
        String result = getContent(entry, repository);

        Assert.assertNotNull("This file cannot be empty/null", result);
    }

    @Test
    public void testGetRevisionsFileNotFound() throws URISyntaxException, SVNException {
        List<SubversionRevision> revisions;

        revisions = getRevisions(
                reposAsURL,
                new Parameters.ParametersBuilder()
                        .setPath("/module2/trunk/")
                        .setEndRevision(1L)
                        .create()
        );

        // check that the search range has been reduced to zero
        Assert.assertTrue(revisions.isEmpty());
    }

    @Test
    public void testGetRevisionsPathDeleted() throws URISyntaxException, SVNException {
        List<SubversionRevision> revisions;

        revisions = getRevisions(
                reposAsURL,
                new Parameters.ParametersBuilder()
                        // file was added in rev2 ans deleted in rev7
                        .setPath("/module2/trunk/playlist.txt")
                        .setEndRevision(8L)
                        .create()
        );
        System.out.println(Objects.toString(revisions));
        // We expect 2 revisions : rev2 and 7
        Assert.assertEquals(revisions.size(), 2);
    }

    @Test
    public void testGetLatestRevision() throws SVNException, URISyntaxException {
        long revision =
                SubversionCrawler.getLatestRevision(
                    reposAsURL,
                    new Parameters.ParametersBuilder().create()
                );
        System.out.println("latest revision :"+revision);
        Assert.assertTrue(revision == 8L);
    }

    @Test
    public void testGetRevisionsMaximumFileSize() throws URISyntaxException, SVNException {
        List<SubversionRevision> result = getRevisions(
            reposAsURL,
            new Parameters.ParametersBuilder()
                .setMaximumFileSize(80L)
                .create()
        );
        int count = 0;
        for (SubversionRevision svnRevision:result) {
            for (SubversionDocument svnDocument : svnRevision.getDocuments()) {
                if (svnDocument.json().contains("size too big")) {
                    count++;
                }
            }
        }
        Assert.assertTrue("We should get 2 documents filtered for being oversized", count == 2);
    }

    @Test
    public void testGetRevisionsFiltered() throws URISyntaxException, SVNException {
        List<SubversionRevision> result = getRevisions(
            reposAsURL,
            new Parameters.ParametersBuilder()
                .setPatternsToFilter(Sets.newHashSet(Pattern.compile("/module2.*")))
                .create()
        );
        int count = 0;
        for(SubversionRevision svnRevision:result) {
            count += svnRevision.getDocuments().size();
        }
        Assert.assertTrue("We should get 7 documents after filter",count == 7);
    }

}
