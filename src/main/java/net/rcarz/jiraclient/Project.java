/**
 * jira-client - a simple JIRA REST client
 * Copyright (c) 2013 Bob Carroll (bob.carroll@alum.rit.edu)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.rcarz.jiraclient;

import com.google.gson.Gson;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.rcarz.jiraclient.Field.PROJECT;

/**
 * Represents a JIRA project.
 */
public class Project extends Resource {

    public static final String JSON_MALFORMED_MSG = "JSON payload is malformed";

    private Map<String, String> avatarUrls = null;
    private String key = null;
    private String name = null;
    private String description = null;
    private User lead = null;
    private String assigneeType = null;
    private List<Component> components = null;
    private List<IssueType> issueTypes = null;
    private List<Version> versions = null;
    private Map<String, String> roles = null;
    private ProjectCategory category = null;
    private String email = null;

    private record RestExceptionResult(
            List<String> errorMessages,
            Map<String, String> errors
    ) {}

    /**
     * Creates a project from a JSON payload.
     *
     * @param restclient REST client instance
     * @param json JSON payload
     */
    protected Project(RestClient restclient, JSONObject json) {
        super(restclient);

        if (json != null)
            deserialise(json);
    }

    private void deserialise(JSONObject json) {
        Map map = json;

        self = Field.getString(map.get("self"));
        id = Field.getString(map.get("id"));
        avatarUrls = Field.getMap(String.class, String.class, map.get("avatarUrls"));
        key = Field.getString(map.get("key"));
        name = Field.getString(map.get("name"));
        description = Field.getString(map.get("description"));
        lead = Field.getResource(User.class, map.get("lead"), restclient);
        assigneeType = Field.getString(map.get("assigneeType"));
        components = Field.getResourceArray(Component.class, map.get("components"), restclient);
        issueTypes = Field.getResourceArray(
            IssueType.class,
            map.containsKey("issueTypes") ? map.get("issueTypes") : map.get("issuetypes"),
            restclient);
        versions = Field.getResourceArray(Version.class, map.get("versions"), restclient);
        roles = Field.getMap(String.class, String.class, map.get("roles"));
        category = Field.getResource(ProjectCategory.class, map.get( "projectCategory" ), restclient);
        email = Field.getString( map.get("email"));
    }

    /**
     * Creates a new project.
     *
     * @param restclient REST client instance
     * @param createMetadata Project creation metadata
     *
     * @return a project instance
     *
     * @throws JiraException when something goes wrong
     */
    public static Project create(RestClient restclient, JSONObject createMetadata)
            throws JiraException {
        try {
            restclient.post(getBaseUri() + PROJECT, createMetadata);
        } catch (Exception ex) {
            throw new JiraException("Failed to create project", ex);
        }

        return get(restclient, createMetadata.getString("key"));
    }


    /**
     * Creates a new project with shared configuration.
     *
     * @param restclient REST client instance
     * @param createMetadata Project creation metadata
     * @param sharedProjectId The shared template project ID
     *
     * @return a project instance
     *
     * @throws JiraException when something goes wrong
     */
    public static Project createShared(RestClient restclient, JSONObject createMetadata, String sharedProjectId)
            throws JiraException {
        try {
            String path = "/rest/project-templates/1.0/createshared/" + sharedProjectId;
            restclient.post(path, createMetadata);
        } catch (RestException ex) {
            if (StringUtils.isNotBlank(ex.getHttpResult())) {
                Gson gson = new Gson();
                RestExceptionResult result = gson.fromJson(ex.getHttpResult() , RestExceptionResult.class);
                if (!result.errors().isEmpty()) {
                    throw new JiraException(String.join(" ", result.errors().values()));
                }
            }
            throw new JiraException(ex.getMessage());
        } catch (IOException | URISyntaxException ex) {
            throw new JiraException(ex.getMessage());
        }

        return get(restclient, createMetadata.getString("key"));
    }


    /**
     * Retrieves the given project record.
     *
     * @param restclient REST client instance
     * @param key Project key
     *
     * @return a project instance
     *
     * @throws JiraException when the retrieval fails
     */
    public static Project get(RestClient restclient, String key)
        throws JiraException {

        JSON result = null;

        try {
            result = restclient.get(getBaseUri() + "project/" + key);
        } catch (Exception ex) {
            throw new JiraException("Failed to retrieve project " + key, ex);
        }

        if (!(result instanceof JSONObject))
            throw new JiraException(JSON_MALFORMED_MSG);

        return new Project(restclient, (JSONObject)result);
    }

    /**
     * Retrieves all project records visible to the session user.
     *
     * @param restclient REST client instance
     *
     * @return a list of projects
     *
     * @throws JiraException when the retrieval fails
     */
    public static List<Project> getAll(RestClient restclient) throws JiraException {
        JSON result = null;

        try {
            result = restclient.get(getBaseUri() + PROJECT);
        } catch (Exception ex) {
            throw new JiraException("Failed to retrieve projects", ex);
        }

        if (!(result instanceof JSONArray))
            throw new JiraException(JSON_MALFORMED_MSG);

        return Field.getResourceArray(Project.class, result, restclient);
    }

    public List<User> getAssignableUsers() throws JiraException {
        JSON result = null;

        try {			
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put(PROJECT, this.key);
            URI searchUri = restclient.buildURI(getBaseUri() + "user/assignable/search", queryParams);
            result = restclient.get(searchUri);
        } catch (Exception ex) {
            throw new JiraException("Failed to retrieve assignable users", ex);
        }

        if (!(result instanceof JSONArray))
            throw new JiraException(JSON_MALFORMED_MSG);

        return Field.getResourceArray(User.class, result, restclient);
    }

    @Override
    public String toString() {
        return getName();
    }

    public Map<String, String> getAvatarUrls() {
        return avatarUrls;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public User getLead() {
        return lead;
    }

    public String getAssigneeType() {
        return assigneeType;
    }

    public List<Component> getComponents() {
        return components;
    }

    public List<IssueType> getIssueTypes() {
        return issueTypes;
    }

    public List<Version> getVersions() {
        return versions;
    }

    public Map<String, String> getRoles() {
        return roles;
    }

    public ProjectCategory getCategory() {
        return category;
    }

    public String getEmail() {
        return email;
    }
}

