package com.clinic.neochild.navigation

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val MANAGE_STAFF = "manage_staff"
    const val ADD_PATIENT = "add_patient"
    const val PATIENT_LIST = "patient_list"
    const val ADD_VACCINE = "add_vaccine"
    const val ADD_VACCINE_FOR_PATIENT = "add_vaccine/{patientId}"
    const val PATIENT_DETAILS = "patient_details/{patientId}"
    const val EDIT_PATIENT = "edit_patient/{patientId}"
    const val EDIT_VACCINATION = "edit_vaccination/{vaccinationId}"
    const val VACCINE_INVENTORY = "vaccine_inventory"
    const val ADD_VACCINE_STOCK = "add_vaccine_stock"
    const val EDIT_VACCINE_STOCK = "edit_vaccine_stock/{vaccineId}"
    const val STATISTICS = "statistics"
    const val BORROWED = "borrowed"
    const val DUE = "due"
    const val WASTE = "waste"
    const val MONTHLY_FINANCE_DETAILS = "monthly_finance_details/{monthKey}"
}
