package io.jenkins.plugins.argocdrollback;

import io.jenkins.plugins.argocdrollback.model.Ordering;
import io.jenkins.plugins.argocdrollback.model.ResultContainer;
import kong.unirest.*;
import kong.unirest.json.JSONObject;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import kong.unirest.json.JSONArray;


public class ArgoCDRollbackParameter {

    private static final Logger logger = Logger.getLogger(ArgoCDRollbackParameter.class.getName());
    private static final Interceptor errorInterceptor = new ErrorInterceptor();

    private ArgoCDRollbackParameter() {
        throw new IllegalStateException("Utility class");
    }

    public static ResultContainer<List<String>> getRollbackVersions(String appName, String argoCDBaseURL,
                                                        String token, Ordering ordering) {
        ResultContainer<List<String>> container = new ResultContainer<>(Collections.emptyList());

        ResultContainer<List<String>> tags = getRollbackVersionsFromArgoCD(appName, argoCDBaseURL, token);

        if (tags.getErrorMsg().isPresent()) {
            container.setErrorMsg(tags.getErrorMsg().get());
            return container;
        }

        ResultContainer<List<String>> filterTags = sortRollbackVersion(tags.getValue(), ordering);
        filterTags.getErrorMsg().ifPresent(container::setErrorMsg);
        container.setValue(filterTags.getValue());
        return container;
    }

    private static ResultContainer<List<String>> sortRollbackVersion(List<String> tags, Ordering ordering) {
        ResultContainer<List<String>> container = new ResultContainer<>(Collections.emptyList());
        logger.info("Ordering Tags according to: " + ordering);

        Comparator<String> rollbackVersionComparator = Comparator.comparing(item -> Integer.valueOf(item.split("\\|")[0].trim()));
        container.setValue(tags.stream()
            .sorted(ordering == Ordering.DESCENDING ? rollbackVersionComparator.reversed() : rollbackVersionComparator)
            .collect(Collectors.toList()));

        return container;
    }
    
    private static String getJakartaTime(String input) {
        DateFormat formatInput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        DateFormat formatOutput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
        formatOutput.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        Date date;
        String formatted = "";
        try {
            date = formatInput.parse(input);
            formatted = formatOutput.format(date);
        } catch (ParseException ex) {
            Logger.getLogger(ArgoCDRollbackParameter.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return formatted;
    }

    private static ResultContainer<List<String>> getRollbackVersionsFromArgoCD(String appName, String argoCDBaseURL, String token) {
        ResultContainer<List<String>> resultContainer = new ResultContainer<>(new ArrayList<>());
        String url = argoCDBaseURL + "/api/v1/applications/" + appName;

        Unirest.config().reset();
        Unirest.config().enableCookieManagement(false).interceptor(errorInterceptor);
        Unirest.config().verifySsl(false);
        HttpResponse<JsonNode> response;
        if (!token.isEmpty()) {
             response= Unirest.get(url).header("Cookie", "argocd.token=" + token).asJson();
        }
        else {
            response= Unirest.get(url).asJson();
        }
        if (response.isSuccess()) {
            logger.info("HTTP status: " + response.getStatusText());
            JSONArray history = response.getBody().getObject()
                .getJSONObject("status")
                .getJSONArray("history");
            for (int i=0; i<history.length(); i++) {
                JSONObject rollbackVersionJSON = history.getJSONObject(i);
                String rollbackVersion = rollbackVersionJSON.getString("id")
                    + " | " + getJakartaTime(rollbackVersionJSON.getString("deployedAt"))
                    + " | " + rollbackVersionJSON.getJSONObject("source")
                        .getJSONObject("kustomize").getJSONArray("images").toList().toString();
                resultContainer.getValue().add(rollbackVersion);
            }
        } else {
            logger.warning("HTTP status: " + response.getStatusText());
            resultContainer.setErrorMsg("HTTP status: " + response.getStatusText());
        }
        Unirest.shutDown();

        return resultContainer;
    }
}
