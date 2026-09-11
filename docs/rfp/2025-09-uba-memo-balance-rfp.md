# UBA — Request for Proposal (RFP): Memo Balance Process

> Primary source. Transcribed from the RFP PDF supplied by the user (prepared by Adeola Olarinde, UBA CIO, September 2025) so future agent sessions have it as an in-repo reference instead of a chat transcript.
>
> Contains UBA staff names, emails, and phone numbers — fine for this local, remote-less repo. Do not push this file if a public remote is ever added.

## Acronyms

| S/N | Term/Acronym | Description |
|---|---|---|
| 1 | UBA | United Bank for Africa |
| 2 | CRP | Customer Request Portal |
| 3 | MBP | Memo Balance Portal |

## 1. Introduction

UBA is a leading financial institution committed to delivering exceptional customer service and operational efficiency. To enhance its ability to manage customer account inquiries and ensure seamless debt exposure monitoring, the bank seeks to improve its current processes for handling customer exposure requests. As part of this effort, UBA is introducing a Customer Exposure Inquiry Portal.

This portal will enable the bank to provide real-time visibility into customer memo balances, streamline account verification processes, and automate critical steps such as liquidation updates and account clearance on credit reporting systems. The goal is to simplify the inquiry process for customers, reduce response times, and improve overall accuracy and compliance in debt exposure management.

## 2. Project Overview

The proposed solution aims to address inefficiencies in the current memo balance management process. The existing workflow relies heavily on manual tracking using Excel spreadsheets and disconnected systems, which limits accuracy, delays updates, and increases operational overhead. Key challenges include real-time memo balance calculations, slow updates after liquidation, manual account verification, and delays in clearing accounts from credit reporting systems like the ICAD portal.

The objective of this project is to implement a Customer Exposure Inquiry Portal that automates the end-to-end process of memo balance management. The system will integrate functionalities such as real-time balance calculations, automated account clearance, partial payment adjustments, audit trail logging, and dashboard reporting. This solution will improve efficiency, enhance data accuracy, and ensure compliance with internal credit policies and regulatory standards.

The proposed platform will also enable seamless data integration across key systems, such as Vision and ICAD, while maintaining calculation consistency. It will support collaboration across multiple teams, including Branch Operations, Transaction Services, Credit Admin, and Credit Operations, by providing a centralized interface with role-based access controls and region based for Nigeria and Africa. Scalable and secure, the solution will ensure the bank's ability to manage customer debt exposure inquiries with greater transparency and reliability.

## 3. Functional Requirements and Specifications

This section outlines the essential features and functionalities required for the proposed **Memo Balance Process**.

### 3.1 Memo Balance Calculation and Adjustment

- The solution must provide **real-time calculation of memo balances** for customer accounts as of the present date.
- The system must automatically **adjust memo balances** to reflect partial payments and bank-approved write-offs.
- The solution must ensure **consistency in balance calculations**, leveraging algorithms from Vision to avoid discrepancies.

### 3.2 Account Verification and Integration

- The system must support seamless **integration with Vision and Excel files** for accurate customer and loan data retrieval.
- The solution must enable automated **account verification** against both internal databases (e.g., Vision) and external data sources, such as ICAD and Memo files.
- The system must cross-reference multiple accounts for customers, ensuring all associated accounts are captured during verification.

### 3.3 Loan Status Management

- The solution must categorize and track customer accounts into distinct segments, such as **"Owing"** and **"Paid-Off"**.
- The system must automatically move customers from the **"Owing"** segment to the **"Paid-Off"** segment upon full liquidation of memo balances.
- The solution must escalate discrepancies in loan statuses to **Credit Admin** for prompt resolution.

### 3.4 Alerts and Notifications

- The system must provide **automated notifications** for the following:
  - Memo balance updates following liquidation.
  - Accounts flagged for review by Transaction Services or Credit Admin.
  - Overdue updates to credit reporting systems (e.g., ICAD).
- The solution must escalate **unresolved action items** to relevant stakeholders within defined timelines.

### 3.5 Audit Trail and Tracking

- The system must maintain a **detailed audit trail** of all actions, including account verification, memo balance updates, and liquidation approvals.
- The solution must track user activities, including logins, data edits, and escalations, to ensure accountability.
- The system must provide drill-down capabilities to review specific changes and updates.

### 3.6 Dashboard and Reporting

- The solution must include a **centralized dashboard** to monitor key metrics, such as:
  - Memo balances.
  - Loan exposure status.
  - Customer account updates.
- The system must generate **customizable reports** for stakeholders, summarizing loan exposures, repayments, and exceptions.
- The system must support **real-time reporting**, allowing users to analyze data trends and performance metrics.

### 3.7 Role-Based Access Control (RBAC)

- The system must implement **role-based access** for various teams:
  - **CSM**: View-only access to memo balances and account details.
  - **Recovery Team**: Full privileges for verification, liquidation tracking, and reporting.
  - **Transaction Services**: Edit and update privileges for account data and memo balances.
  - **Credit Admin**: Full privileges for verification, liquidation tracking, and reporting.
  - **Maxim Team**: View and manage Excel data integration processes.
- The solution must log any changes to user roles and permissions for compliance.

### 3.8 ICAD Clearance and Automation

- The system must automatically **clear customer names** from the ICAD portal within 24–48 hours after liquidation of memo balances.
- The solution must integrate with ICAD to provide **real-time updates**, reducing manual intervention.

### 3.9 Action Plan Tracking and Resolution

- The solution must track all **pending action plans**, such as liquidation updates, verification requests, and ICAD escalations.
- The system must issue alerts for **overdue action plans** and escalate unresolved items to stakeholders.
- The system must track corrective actions to ensure resolution within specified timelines.

### 3.10 Partial Payments and Exceptions Handling

- The system must adjust memo balances dynamically to reflect **partial payments** made by customers.
- The solution must identify and escalate exceptions, such as **unallocated payments** or **discrepancies in account balances**, to Transaction Services.

### 3.11 Document Management

- The system must securely store and manage documents related to:
  - Non-indebtedness letters.
  - Customer account verification records.
  - Memo file updates.
- The solution must support multiple file formats for easy document upload and retrieval.

### 3.12 Continuity of Process Metrics

- The system must monitor critical **process metrics**, such as:
  - Average response time for memo balance inquiries.
  - Time taken for ICAD clearance after liquidation.
- The system must provide stakeholders with **exception reports** for process deviations.
- Management Information (MI) reports must include corrective actions for process improvements.

### 3.13 System Integration and Scalability

- The solution must support plug-ins for existing systems (e.g., Vision, ICAD) and be scalable to handle increasing data volumes.
- The system must provide **real-time updates** and seamless integration with other platforms to ensure data consistency across departments.

### 3.13 (bis) Automated Tracking & Management of Memo Balance via Core Banking Integration

> Numbered 3.13 in the source document a second time; preserved as-is.

- **(Detection)**: The system shall detect accounts moved to Memo by scanning Finacle movement/transaction narrations for the phrase **"written off"** (case-insensitive, with configurable variants such as *written-off* / *write off*).
- **(Flag & Create/Update)**: On detection, the system shall automatically **flag** the account as a Memo account and **create or update** the corresponding record in the Memo database.
- **(Balance Capture)**: The system shall retrieve and store the **current account balance/outstanding** from Finacle at the time of detection.
- **(Transfer Date Capture)**: The system shall capture and store the **effective transfer date/time** the account was moved to Memo, as indicated by the relevant Finacle posting/movement.
- **(Metadata)**: The system shall persist key attributes with the Memo record, including **Account Number, Customer ID, Branch/SOL, Currency, Transaction/Posting Reference, and full Narration text**.
- **(De-duplication)**: If a Memo record already exists for the account, the system shall **link the detection to the existing record** and **update the balance** without creating duplicates; the **earliest transfer date** must be preserved.
- **(Status Visibility)**: Newly detected accounts shall be surfaced in the portal with a clear status (e.g., **"Imported — Pending Review"**) for operations users.
- **(Audit)**: Each detection and ingestion event shall generate an **audit log entry** capturing timestamp, source, matched phrase, and affected record identifiers.

### 3.14 Country & Configuration

- Region → Country dropdown (Region defaults to **Africa & Nigeria**; Country includes Nigeria and other subsidiaries).
- Country parameters: base currency, GL mappings (write-off, recovery), date format, holiday calendar.
- Country scoping of users by default; cross-country roles configurable.
- Admin UI to add/edit countries and mappings without code.
- Country is chosen for which memo accounts are to be uploaded for.
- Interface with the core banking for all countries to monitor inflows.

### 3.15 Reporting

- **(Frequencies & Windows)**: The system shall generate **Weekly**, **Monthly**, and **Quarterly** reports using closed periods: Weekly = Mon–Sun (configurable), Monthly = calendar month, Quarterly = fiscal/calendar quarter (configurable).
- **(Scheduling)**: Report jobs shall run on a **configurable schedule** (default 08:00 WAT on first business day after period end) with **retry** and **rerun on demand**.
- **(Scopes/Filters)**: Each report shall support filters for **Country, Region, Directorate, SOL/Branch, Product/Segment, Currency, Amount bands, Date range**.
- **(New Memo Accounts)**: Each period's report shall show **count and list** of **newly flagged Memo accounts** (first time in Memo within the period) with columns: Account No, Customer ID, SOL, Region, Directorate, First Memo Date/Time, Currency.
- **(Account Balances – All)**: Each period's report shall present **closing balances (as-of period end)** for all **relevant accounts** with columns: Account No, In-Memo (Y/N), Currency, Closing Balance, SOL, Region, Directorate.
- **(Memo Account Balances – In-Memo Only)**: A focused view for **Memo accounts only** showing: Account No, Currency, Closing Memo Balance, Transfer-to-Memo Date, SOL, Region, Directorate; plus **period movement** (opening, inflows, outflows, closing).
- **(Sweep to P&L)**: The report shall aggregate **write-off/sweep-to-P&L postings** that occurred in the period, grouped by **SOL, Region, Directorate**, with columns: Posting Date, Journal Ref/GL, Amount, Currency, Account No (where applicable).
- **(Aggregations & Totals)**: All reports shall provide **sub-totals by SOL, Region, Directorate** and **grand totals**, with drill-down to underlying records.
- **(As-of & FX Handling)**: Reports shall display the **As-of timestamp** and show amounts in **local currency** and (if configured) **group currency** using the **period-end FX rate**.
- **(Delivery & Formats)**: Reports shall be accessible in the portal and distributable via email in **XLSX/CSV/PDF**; filenames include report type, period, and timestamp; large files are **zipped**.
- **(Recipients & Access)**: **Role-based** recipient lists are configurable per report/frequency. Users may only see rows for their permitted **Country/Region/Directorate/SOL**.
- **(Data Lineage & Audit)**: Each report run shall log **job ID, period, filters, row counts, totals, data extract version**, and distribution status; logs are exportable.
- **(Validation & Reconciliation)**: The system shall validate that **Memo totals** reconcile to the **Write-Off Register/GL sweep totals** for the period; discrepancies above a configurable threshold are **flagged** in the report header.
- **(Retention)**: Generated reports and their source snapshots shall be **retained** for a configurable period (default **7 years**) with secure storage and retrieval.
- **(Config Tables)**: The system shall maintain mapping tables for **SOL → Region → Directorate** and **GL codes for P&L sweeps**, versioned and auditable; reports must use the **effective mapping** for the period.
- **(Performance)**: Report generation shall support **minimum 100k rows per period** and complete within a **configurable SLA**; partial failures must not publish and must alert owners.

> Vendors must respond to each required functionality/feature alongside the proposal.

## 4. Technical Requirements

The proposed solution for the **Memo Balance Process** must meet the following technical specifications:

### 4.1 Integration Capabilities

- The solution must integrate seamlessly with UBA's existing systems, including Vision, ICAD, and other financial platforms.
- The system must enable real-time data synchronization between Vision, Excel sheets, and other external data sources.
- The solution must allow plug-ins for legacy systems to ensure smooth data import and export processes.
- Integration must automate workflows, minimizing manual intervention for memo balance updates, liquidation, and customer account verifications.

### 4.2 Scalability

- The solution must handle large volumes of customer and loan data without performance degradation.
- The system must support an increasing number of users across multiple business units and geographic locations.
- The solution must allow future additions of modules and functionalities, such as enhanced reporting or extended ICAD integrations, with minimal infrastructure changes.

### 4.3 Security and Compliance

- The system must support **multi-factor authentication (MFA)** and implement **role-based access control (RBAC)** for all users, ensuring data security.
- All customer and loan data must be encrypted end-to-end, both in transit and at rest.
- The solution must maintain comprehensive audit logs to track user actions and ensure traceability.
- The system must comply with international data security standards, such as **ISO 27001** and relevant data protection regulations, including **GDPR** where applicable.
- The system must enforce session timeouts for inactive users, with shorter timeouts for access to sensitive data or transactions.

### 4.4 Automation and Reporting

- The solution must automate memo balance calculations and provide real-time updates for partial payments, write-offs, and customer liquidations.
- The system must generate reports on memo balances, customer statuses, loan exposures, and liquidation progress.
- The solution must include customizable report templates for use by various stakeholders, including Credit Admin, Transaction Services, and senior management.
- Automated notifications and alerts must be triggered for overdue tasks, escalations, and discrepancies in memo balances or loan statuses.

### 4.5 User Management and Alerts

- The solution must provide centralized user management with role-based access and permissions for teams, such as:
  - View-only access for CSMs.
  - Edit and management privileges for Transaction Services, Credit Admin, and Admin teams.
- The system must send automatic reminders and alerts for pending or overdue actions, such as ICAD clearances or liquidation approvals.
- Administrators must be able to modify user roles and permissions via a centralized control panel.

### 4.6 ICAD Automation and Third-Party Integration

- The system must automate customer name clearance from ICAD within **24–48 hours** post-liquidation, eliminating manual delays.
- The solution must integrate with third-party platforms (e.g., ICAD) for efficient data sharing and tracking of customer accounts.
- The system must issue notifications to stakeholders if discrepancies arise during ICAD clearance processes.

### 4.7 System Performance and Reliability

- The solution must ensure **99.9% uptime**, minimizing disruptions during system maintenance and upgrades.
- The system must support multiple concurrent users, enabling real-time updates and processing of high transaction volumes.
- The solution must include robust backup and disaster recovery mechanisms to prevent data loss and ensure system reliability in case of failures.

### 4.8 Real-Time Customer Balance Management

- The system must calculate memo balances in real time, ensuring up-to-date information is available for all users.
- The solution must dynamically adjust balances for partial payments and approved write-offs without requiring manual intervention.
- Key identifiers (e.g., Account No, Account Name, Principal Amount, Unique No) must be used for customer and loan tracking.

### 4.9 Maintenance and Support

- The vendor must provide ongoing maintenance, ensuring system updates, bug fixes, and support are delivered promptly.
- The vendor must offer a help desk for resolving technical issues and provide training for end users during and after deployment.

### 4.10 Accessibility and Flexibility

- The solution must be accessible through both web and mobile platforms, ensuring availability for staff across various locations.
- The system must offer flexibility to adapt to specific workflows, such as integrating additional customer verification processes or ICAD enhancements.
- The solution should enable configuration of alerts, thresholds, and reporting templates to meet organizational requirements.

## 5. Vendor Requirements

Vendor must demonstrate their capability to deliver the proposed solution, including:

1. Relevant experience in delivering similar solutions
2. Technical and functional expertise
3. Strong project management and support capabilities
4. Ability to meet the timeline
5. Demonstrate knowledge of industry-specific regulations, including Basel, GDPR, SOX, and other relevant frameworks.
6. Detail their project management approach, including timelines for implementation and training.

## 6. Evaluation Criteria

Proposals will be evaluated based on the following criteria:

1. Technical capability and approach
2. Experience and qualifications
3. Cost-effectiveness
4. Implementation timeline
5. Support and maintenance offerings

## 7. Guidelines for Proposal Preparation

The proposals are suggested to include each of the following sections:

1. **Executive Summary** — a high-level synopsis of the Vendor's responses to the RFP, identifying the main features and benefits of the proposed project.
2. **Project Management Approach** — the method and approach used to manage the overall project, briefly describing how the project will proceed from beginning to end.
3. **Deliverables** — comprehensive feature listings of the solution and description, sample reports the system will provide, the technology platform, and the delivery timeline.
4. **Detailed and Itemized Pricing** — a fee breakdown for Development, licensing and options, and Product support pricing.
5. **References** — reference contacts, sites and locations where the solution is currently being used (where applicable).
6. **Company Overview** — brief history, including year established and number of years the company has been offering technology solutions.
7. **Timetable for Solution Evaluation**:

| Milestone | Date |
|---|---|
| RFP Release | December 29th, 2024 |
| Submission of RFP | December 10th – December 13th, 2024 |
| Vendor Evaluation | December 16th – December 20th, 2024 |

> Dates as given in the source RFP (the submission/evaluation window predates the release date in the original document — preserved as-is, not corrected).

## 8. Contact

Technical and business requirements proposal or concerns should be directed to:

- **Nwakaego Chijioke** — Project Manager, CIO — nwakaego.chijioke@ubagroup.com — +234-8106790190
- **Adeola Olarinde** — Business Analyst, CIO — Adeola.olarinde@ubagroup.com — +234-8034844127
- **Oluwaseun Apata** — Divisional CIO — Oluwaseun.apata@ubagroup.com — +234-8039446128

Commercial proposal (soft copy) should be directed to:

- **Ubaka Ugbene** — Head, Project Management CIO Org. — Ubaka.ugbene@ubagroup.com — +234-8166397879
- **Nwakaego Chijioke** — Project Manager, CIO — nwakaego.chijioke@ubagroup.com — +234-8106790190
