package com.softnexis.contactmanager;

import com.softnexis.contactmanager.controller.AddContactServlet;
import com.softnexis.contactmanager.controller.ContactServlet;
import com.softnexis.contactmanager.model.Contact;
import com.softnexis.contactmanager.service.InMemoryContactService;
import com.softnexis.contactmanager.util.CsrfTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Servlet Controller Tests")
public class ContactServletTest {

    private ContactServlet contactServlet;
    private AddContactServlet addContactServlet;
    private InMemoryContactService contactService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher requestDispatcher;

    private Map<String, Object> sessionAttributes;
    private Map<String, Object> requestAttributes;

    @BeforeEach
    void setUp() {
        contactService = new InMemoryContactService();

        contactServlet = new ContactServlet();
        addContactServlet = new AddContactServlet();

        contactServlet.setContactService(contactService);
        addContactServlet.setContactService(contactService);
        contactServlet.setAddContactServlet(addContactServlet);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        requestDispatcher = mock(RequestDispatcher.class);

        sessionAttributes = new HashMap<>();
        requestAttributes = new HashMap<>();

        when(request.getSession(anyBoolean())).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(request.getContextPath()).thenReturn("/test");
        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return sessionAttributes.get(key);
        }).when(session).getAttribute(anyString());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object val = invocation.getArgument(1);
            sessionAttributes.put(key, val);
            return null;
        }).when(session).setAttribute(anyString(), any());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object val = invocation.getArgument(1);
            requestAttributes.put(key, val);
            return null;
        }).when(request).setAttribute(anyString(), any());
    }

    @Test
    @DisplayName("GET /contacts displays contact list and forwards to contact-list.jsp")
    void testGetContactsList() throws Exception {
        when(request.getServletPath()).thenReturn("/contacts");
        when(request.getPathInfo()).thenReturn(null);

        contactServlet.doGet(request, response);

        verify(request).getRequestDispatcher("/contact-list.jsp");
        verify(requestDispatcher).forward(request, response);
        assertTrue(requestAttributes.containsKey("contacts"));
        assertTrue(requestAttributes.containsKey("currentPage"));
        assertTrue(requestAttributes.containsKey("totalPages"));
    }

    @Test
    @DisplayName("GET /contacts/add displays contact form and sets CSRF token")
    void testGetContactsAdd() throws Exception {
        when(request.getServletPath()).thenReturn("/contacts");
        when(request.getPathInfo()).thenReturn("/add");

        contactServlet.doGet(request, response);

        verify(request).getRequestDispatcher("/contact-form.jsp");
        verify(requestDispatcher).forward(request, response);
        assertTrue(requestAttributes.containsKey("csrfToken"));
        assertNotNull(requestAttributes.get("csrfToken"));
    }

    @Test
    @DisplayName("POST /contacts with valid data saves contact and returns 302 redirect")
    void testPostValidContactSubmission() throws Exception {
        String token = CsrfTokenManager.getToken(session);

        when(request.getParameter("name")).thenReturn("Bruce Wayne");
        when(request.getParameter("email")).thenReturn("bruce@wayne-enterprises.com");
        when(request.getParameter("phone")).thenReturn("5551234567");
        when(request.getParameter(CsrfTokenManager.CSRF_TOKEN_PARAM)).thenReturn(token);

        addContactServlet.doPost(request, response);

        // Verify 302 Redirect to /contacts
        verify(response).sendRedirect("/test/contacts");

        // Verify contact is in service
        boolean found = contactService.getAllContacts(session).stream()
                .anyMatch(c -> "bruce@wayne-enterprises.com".equals(c.getEmail()));
        assertTrue(found, "Saved contact must exist in contact store");

        // Verify flash toast was set
        assertTrue(sessionAttributes.containsKey("flashToast"));
    }

    @Test
    @DisplayName("POST /contacts with empty form -> Field-specific errors and 400 Bad Request")
    void testPostEmptyFormSubmission() throws Exception {
        String token = CsrfTokenManager.getToken(session);

        when(request.getParameter("name")).thenReturn("");
        when(request.getParameter("email")).thenReturn("");
        when(request.getParameter("phone")).thenReturn("");
        when(request.getParameter(CsrfTokenManager.CSRF_TOKEN_PARAM)).thenReturn(token);

        addContactServlet.doPost(request, response);

        // Verify HTTP 400 Bad Request
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

        // Verify forward back to contact-form.jsp
        verify(request).getRequestDispatcher("/contact-form.jsp");
        verify(requestDispatcher).forward(request, response);

        // Verify error attributes
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) requestAttributes.get("errors");
        assertNotNull(errors);
        assertEquals("Please enter valid name", errors.get("name"));
        assertEquals("Invalid email address", errors.get("email"));
    }

    @Test
    @DisplayName("POST /contacts with invalid email -> 'Invalid email address' error")
    void testPostInvalidEmailSubmission() throws Exception {
        String token = CsrfTokenManager.getToken(session);

        when(request.getParameter("name")).thenReturn("Clark Kent");
        when(request.getParameter("email")).thenReturn("notanemail");
        when(request.getParameter("phone")).thenReturn("5551234567");
        when(request.getParameter(CsrfTokenManager.CSRF_TOKEN_PARAM)).thenReturn(token);

        addContactServlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(request).getRequestDispatcher("/contact-form.jsp");

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) requestAttributes.get("errors");
        assertNotNull(errors);
        assertEquals("Invalid email address", errors.get("email"));
    }

    @Test
    @DisplayName("POST /contacts with invalid phone -> 'Use 10-digit format' error")
    void testPostInvalidPhoneSubmission() throws Exception {
        String token = CsrfTokenManager.getToken(session);

        when(request.getParameter("name")).thenReturn("Barry Allen");
        when(request.getParameter("email")).thenReturn("barry@starlabs.com");
        when(request.getParameter("phone")).thenReturn("12345"); // Invalid format
        when(request.getParameter(CsrfTokenManager.CSRF_TOKEN_PARAM)).thenReturn(token);

        addContactServlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) requestAttributes.get("errors");
        assertNotNull(errors);
        assertEquals("Use 10-digit format", errors.get("phone"));
    }

    @Test
    @DisplayName("POST /contacts with missing CSRF token is rejected with 400 Bad Request")
    void testCsrfProtection() throws Exception {
        when(request.getParameter("name")).thenReturn("Hacker");
        when(request.getParameter("email")).thenReturn("hacker@bad.org");
        when(request.getParameter("phone")).thenReturn("");
        when(request.getParameter(CsrfTokenManager.CSRF_TOKEN_PARAM)).thenReturn("invalid-token");

        addContactServlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verify(request).getRequestDispatcher("/contact-form.jsp");
        assertTrue(requestAttributes.containsKey("error"));
    }
}
