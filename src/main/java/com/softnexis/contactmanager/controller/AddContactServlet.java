package com.softnexis.contactmanager.controller;

import com.softnexis.contactmanager.model.Contact;
import com.softnexis.contactmanager.service.ContactService;
import com.softnexis.contactmanager.service.InMemoryContactService;
import com.softnexis.contactmanager.util.ContactValidator;
import com.softnexis.contactmanager.util.CsrfTokenManager;
import com.softnexis.contactmanager.util.ValidationResult;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Controller servlet for handling contact form submissions.
 * Handles POST /contacts:
 * - Validates input data (Name, Email, Phone)
 * - Redirects to success view (302 Redirect to /contacts) or returns errors (Forward to contact-form.jsp)
 */
@WebServlet(name = "AddContactServlet", urlPatterns = {"/contacts/add-submit", "/contacts/submit"})
public class AddContactServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private ContactService contactService;

    @Override
    public void init() throws ServletException {
        super.init();
        this.contactService = InMemoryContactService.getInstance();
    }

    public void setContactService(ContactService contactService) {
        this.contactService = contactService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Redirect accidental GET to the contact form
        response.sendRedirect(request.getContextPath() + "/contacts/add");
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processAddContact(request, response);
        } catch (NullPointerException e) {
            // Error Handling: Handle NullPointerException gracefully
            log("Null pointer encountered during contact creation", e);
            request.setAttribute("error", "An unexpected error occurred while processing the contact. Please try again.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.getRequestDispatcher("/contact-form.jsp").forward(request, response);
        } catch (Exception e) {
            log("Server error during contact creation", e);
            request.setAttribute("error", "Server processing error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.getRequestDispatcher("/contact-form.jsp").forward(request, response);
        }
    }

    private void processAddContact(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Session expiration management
        HttpSession session = request.getSession(true);

        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");

        // CSRF verification
        if (!CsrfTokenManager.isValidToken(request)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("error", "Session expired or invalid security token. Please resubmit the form.");
            preserveFormInput(request, name, email, phone);
            request.setAttribute("csrfToken", CsrfTokenManager.getToken(session));
            request.getRequestDispatcher("/contact-form.jsp").forward(request, response);
            return;
        }

        // Server-side validation
        ValidationResult result = ContactValidator.validate(name, email, phone);

        // Check for duplicate email submission
        if (result.isValid() && contactService.isEmailDuplicate(session, email, null)) {
            result.addError("email", "A contact with this email already exists");
        }

        // If invalid data -> Forward with errors to contact-form.jsp (400 Bad Request)
        if (!result.isValid()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("errors", result.getErrors());
            request.setAttribute("error", "Please correct the highlighted fields before submitting.");
            request.setAttribute("firstInvalidField", result.getFirstInvalidField());
            preserveFormInput(request, name, email, phone);
            request.setAttribute("csrfToken", CsrfTokenManager.getToken(session));
            request.getRequestDispatcher("/contact-form.jsp").forward(request, response);
            return;
        }

        // If valid data -> Save contact and 302 Redirect to /contacts
        Contact newContact = new Contact();
        newContact.setName(ContactValidator.sanitize(name));
        newContact.setEmail(ContactValidator.sanitize(email).toLowerCase());
        newContact.setPhone(ContactValidator.normalizePhone(phone));

        contactService.saveContact(session, newContact);

        // Flash message for toast alert on redirected page
        session.setAttribute("flashToast", "Contact \"" + newContact.getName() + "\" added successfully!");
        session.setAttribute("flashToastType", "success");

        // Regenerate CSRF token to prevent duplicate form resubmissions
        CsrfTokenManager.regenerateToken(session);

        // 302 Redirect to /contacts
        response.sendRedirect(request.getContextPath() + "/contacts");
    }

    private void preserveFormInput(HttpServletRequest request, String name, String email, String phone) {
        request.setAttribute("enteredName", ContactValidator.sanitize(name));
        request.setAttribute("enteredEmail", ContactValidator.sanitize(email));
        request.setAttribute("enteredPhone", ContactValidator.sanitize(phone));
    }
}
