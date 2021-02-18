package com.example.demo.servlet;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectIssuesStatistic extends HttpServlet {
    @JiraImport
    private final SearchService searchService;
    @JiraImport
    private final TemplateRenderer templateRenderer;
    @JiraImport
    private final JiraAuthenticationContext authenticationContext;
    private static final String STATISTIC_TEMPLATE = "/templates/statistic.vm";

    public ProjectIssuesStatistic(SearchService searchService,
                                  TemplateRenderer templateRenderer,
                                  JiraAuthenticationContext authenticationContext) {
        this.searchService = searchService;
        this.templateRenderer = templateRenderer;
        this.authenticationContext = authenticationContext;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> context = new HashMap<>();
        resp.setContentType("text/html;charset=utf-8");

        List<Issue> issues = getIssues();

        List<String> allWords = new LinkedList<>();
        issues.forEach(issue -> {
            Collections.addAll(allWords, issue.getSummary().toLowerCase().split("[^a-z0-9]+"));
            Collections.addAll(allWords, issue.getDescription().toLowerCase().split("[^a-z0-9]+"));
        });

        Map<String, Long> wordsAndTheirCounts = allWords.stream()
                .map(String::toLowerCase)
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        List<Map.Entry<String, Long>> mostFrequentWordsAndTheirCounts = wordsAndTheirCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(100)
                .collect(Collectors.toList());
        List<Map.Entry<String, Double>> mostFrequentWordsAndTheirFrequency = mostFrequentWordsAndTheirCounts.stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().doubleValue() / issues.size()))
                .collect(Collectors.toList());

        context.put("numberOfIssues", issues.size());
        context.put("mostFrequentWordsAndTheirFrequency", mostFrequentWordsAndTheirFrequency);
        templateRenderer.render(STATISTIC_TEMPLATE, context, resp.getWriter());
    }

    private List<Issue> getIssues() {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        JqlClauseBuilder jqlClauseBuilder = JqlQueryBuilder.newClauseBuilder();
        Query query = jqlClauseBuilder.project().isNotEmpty().buildQuery();

        SearchResults searchResults = null;
        try {
            searchResults = searchService.search(user, query, PagerFilter.getUnlimitedFilter());
        } catch (SearchException e) {
            e.printStackTrace();
        }
        return searchResults != null ? searchResults.getIssues() : null;
    }
}