package com.softnexis.contactmanager.service;

import com.softnexis.contactmanager.model.Contact;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for Contact business operations.
 */
public interface ContactService {

    /**
     * Retrieves all contacts, maintaining state in session.
     */
    List<Contact> getAllContacts(HttpSession session);

    /**
     * Retrieves a contact by ID.
     */
    Optional<Contact> getContactById(HttpSession session, Long id);

    /**
     * Saves a new contact or updates an existing contact.
     */
    Contact saveContact(HttpSession session, Contact contact);

    /**
     * Deletes a contact by ID.
     */
    boolean deleteContact(HttpSession session, Long id);

    /**
     * Checks if email is already in use by another contact.
     */
    boolean isEmailDuplicate(HttpSession session, String email, Long excludeId);

    /**
     * Filters contacts matching a search query.
     */
    List<Contact> searchContacts(HttpSession session, String query);
}
