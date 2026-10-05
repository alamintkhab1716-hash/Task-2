package com.softnexis.contactmanager.controller;

import com.softnexis.contactmanager.model.Contact;
import com.softnexis.contactmanager.service.ContactService;
import com.softnexis.contactmanager.service.InMemoryContactService;
import com.softnexis.contactmanager.util.CsrfTokenManager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Controller servlet for Contact Manager.
 * Responsibilities:
 * - GET /contacts: Display all contacts with pagination and search
 * - GET /contacts/add: Show contact submission form
 * - POST /contacts: Delegates to AddContactServlet for form submission processing
 * - GET /contacts/delete: Delete a contact
 */
@WebServlet(name = "ContactServlet", urlPatterns = {"/contacts", "/contacts/*"})
public class ContactServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_PAGE_SIZE = 5;

    private ContactService contactService;
    private AddContactServlet addContactServlet;

    @Override
    public void init() throws ServletException {
        super.init();
        this.contactService = InMemoryContactService.getInstance();
        this.addContactServlet = new AddContactServlet();
        this.addContactServlet.init(getServletConfig());
    }

    public void setContactService(ContactService contactService) {
        this.contactService = contactService;
        if (this.addContactServlet != null) {
            this.addContactServlet.setContactService(contactService);
        }
    }

    public void setAddContactServlet(AddContactServlet addContactServlet) {
        this.addContactServlet = addContactServlet;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String path = request.getPathInfo();
            String servletPath = request.getServletPath();

            // Handle routing: /contacts/add, /contacts/delete, or /contacts
            if ("/add".equals(path) || "/contacts/add".equals(servletPath)) {
                showContactForm(request, response);
            } else if ("/delete".equals(path)) {
                handleDeleteContact(request, response);
            } else {
                showContactList(request, response);
            }
        } catch (NullPointerException e) {
            log("Null pointer in ContactServlet.doGet", e);
            request.setAttribute("error", "A system error occurred while loading contacts. Please refresh.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            showContactList(request, response);
        } catch (Exception e) {
            log("Server error in ContactServlet.doGet", e);
            request.setAttribute("error", "Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            showContactList(request, response);
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getPathInfo();
        if ("/delete".equals(path)) {
            handleDeleteContact(request, response);
            return;
        }

        // POST /contacts is handled by AddContactServlet per sequence diagram
        if (addContactServlet != null) {
            addContactServlet.doPost(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "AddContactServlet not initialized");
        }
    }

    /**
     * GET /contacts/add: Shows the contact form with a new CSRF token.
     */
    private void showContactForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        String token = CsrfTokenManager.getToken(session);
        request.setAttribute("csrfToken", token);
        request.getRequestDispatcher("/contact-form.jsp").forward(request, response);
    }

    /**
     * GET /contacts: Displays the contact list with pagination and search.
     */
    private void showContactList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(true);

        // Flash message promotion from session to request attributes
        promoteFlashAttributes(session, request);

        // Search query parameter
        String query = request.getParameter("q");
        List<Contact> allContacts;
        if (query != null && !query.trim().isEmpty()) {
            allContacts = contactService.searchContacts(session, query.trim());
            request.setAttribute("searchQuery", query.trim());
        } else {
            allContacts = contactService.getAllContacts(session);
        }

        // Pagination parameters
        int pageSize = DEFAULT_PAGE_SIZE;
        String pageSizeParam = request.getParameter("size");
        if (pageSizeParam != null) {
            try {
                int parsed = Integer.parseInt(pageSizeParam);
                if (parsed > 0 && parsed <= 50) {
                    pageSize = parsed;
                }
            } catch (NumberFormatException ignored) {}
        }

        int totalContacts = allContacts.size();
        int totalPages = (int) Math.ceil((double) totalContacts / pageSize);
        if (totalPages == 0) totalPages = 1;

        int currentPage = 1;
        String pageParam = request.getParameter("page");
        if (pageParam != null) {
            try {
                currentPage = Integer.parseInt(pageParam);
            } catch (NumberFormatException ignored) {}
        }
        if (currentPage < 1) currentPage = 1;
        if (currentPage > totalPages) currentPage = totalPages;

        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalContacts);

        List<Contact> pagedContacts = (fromIndex < totalContacts)
                ? allContacts.subList(fromIndex, toIndex)
                : Collections.emptyList();

        request.setAttribute("contacts", pagedContacts);
        request.setAttribute("currentPage", currentPage);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("pageSize", pageSize);
        request.setAttribute("totalContacts", totalContacts);

        request.getRequestDispatcher("/contact-list.jsp").forward(request, response);
    }

    /**
     * Handles deleting a contact by ID.
     */
    private void handleDeleteContact(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(true);
        String idParam = request.getParameter("id");
        if (idParam != null) {
            try {
                Long id = Long.parseLong(idParam);
                boolean deleted = contactService.deleteContact(session, id);
                if (deleted) {
                    session.setAttribute("flashToast", "Contact has been removed.");
                    session.setAttribute("flashToastType", "info");
                } else {
                    session.setAttribute("error", "Contact not found.");
                }
            } catch (NumberFormatException e) {
                session.setAttribute("error", "Invalid contact ID.");
            }
        }
        response.sendRedirect(request.getContextPath() + "/contacts");
    }

    /**
     * Promotes flash messages from session to request and removes them from session.
     */
    private void promoteFlashAttributes(HttpSession session, HttpServletRequest request) {
        if (session == null) return;

        Object flashToast = session.getAttribute("flashToast");
        if (flashToast != null) {
            request.setAttribute("flashToast", flashToast);
            request.setAttribute("flashToastType", session.getAttribute("flashToastType"));
            session.removeAttribute("flashToast");
            session.removeAttribute("flashToastType");
        }

        Object flashSuccess = session.getAttribute("successMessage");
        if (flashSuccess != null) {
            request.setAttribute("successMessage", flashSuccess);
            session.removeAttribute("successMessage");
        }

        Object flashError = session.getAttribute("error");
        if (flashError != null) {
            request.setAttribute("error", flashError);
            session.removeAttribute("error");
        }
    }
}
