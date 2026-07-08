package com.clinic.neochild.utils

/**
 * Centralized constants for the application to ensure consistency.
 */
object Constants {
    // Admin email addresses for managing staff
    val ADMIN_EMAILS = listOf(
        "anjumnadeem580@gmail.com"
    )

    // Standard date format used across the entire app (e.g., 9 May 2026)
    const val DATE_FORMAT = "d MMM yyyy"
    
    // Pre-defined list of common vaccines for suggestions in the Add Vaccination screen
    val COMMON_VACCINES = listOf(
        "BCG", "Hepatitis B (HepB)", "OPV", "IPV", "Pentavalent (Penta)", "Rotavirus (RV)", "PCV", "fIPV", "Vitamin A", 
        "DPT Booster", "Measles-Rubella (MR)", "JE", "TD", "Flu (Influenza)", "Typhoid", "MMR", "Chickenpox (Varicella)", 
        "Hepatitis A (HepA)", "HPV", "Meningococcal", "Cholera", "Rabies"
    )
}
