<template>
  <div class="legal-page">
    <section class="page-hero">
      <div class="container">
        <p class="kicker">Legal</p>
        <h1 class="page-title">Privacy Policy</h1>
        <p class="legal-updated">Last updated: September 1, 2026</p>
      </div>
    </section>

    <section class="section">
      <div class="container legal-content">
        <p>
          {{ company }}. ("{{ brand }}", "we", "us", or "our") respects your privacy and is
          committed to protecting your personal data. This Privacy Policy explains what information we
          collect, how we use it, and the choices you have regarding your data.
        </p>

        <h2>1. Information We Collect</h2>
        <h3>1.1 Information You Provide</h3>
        <ul>
          <li><strong>Contact Information:</strong> Name and email address when you submit our contact form or subscribe to our newsletter.</li>
          <li><strong>Billing Information:</strong> When you make a purchase, we collect your name and email. Payment card details are handled directly by our payment processor, Stripe, and never stored on our servers.</li>
          <li><strong>Account Information:</strong> Email address and password (hashed) if you create a streaming account.</li>
        </ul>
        <h3>1.2 Information Collected Automatically</h3>
        <ul>
          <li><strong>Usage Data:</strong> Pages viewed, dramas watched, time spent, and device type.</li>
          <li><strong>Technical Data:</strong> IP address, browser type, operating system, and approximate location (country level).</li>
          <li><strong>Cookies:</strong> Session identifiers and preference cookies (see Section 5 below).</li>
        </ul>

        <h2>2. How We Use Your Information</h2>
        <ul>
          <li>To provide, operate, and maintain the streaming service;</li>
          <li>To process transactions and deliver purchased content;</li>
          <li>To send service announcements, billing notices, and policy updates;</li>
          <li>To respond to your inquiries and provide customer support;</li>
          <li>To analyze usage patterns and improve our content and platform;</li>
          <li>To detect, prevent, and address fraud, abuse, and security issues.</li>
        </ul>

        <h2>3. Legal Basis for Processing</h2>
        <p>
          For users in the European Economic Area (EEA), United Kingdom, and other jurisdictions with
          applicable data protection laws, we process personal data based on:
        </p>
        <ul>
          <li><strong>Consent:</strong> When you provide information voluntarily (e.g., contact form, newsletter);</li>
          <li><strong>Contractual necessity:</strong> To fulfill our obligations when you purchase a subscription or credits;</li>
          <li><strong>Legitimate interests:</strong> To operate and secure our platform, prevent fraud, and improve our services;</li>
          <li><strong>Legal compliance:</strong> To meet applicable legal and tax obligations.</li>
        </ul>

        <h2>4. Data Sharing and Third Parties</h2>
        <p>We do not sell your personal data. We share information only with the following categories of service providers:</p>
        <ul>
          <li><strong>Stripe:</strong> Payment processing and fraud prevention. Stripe processes your card data under its own PCI-DSS compliance. See <a href="https://stripe.com/privacy" target="_blank" rel="noopener">Stripe's Privacy Policy</a>.</li>
          <li><strong>Cloud &amp; CDN Providers:</strong> Content delivery networks to stream video efficiently worldwide.</li>
          <li><strong>Email Service Providers:</strong> To deliver transactional and support emails.</li>
          <li><strong>Analytics Providers:</strong> Aggregated, anonymized usage statistics.</li>
        </ul>
        <p>We may also disclose data when required by law, court order, or to protect our rights and safety.</p>

        <h2>5. Cookies</h2>
        <p>We use the following types of cookies:</p>
        <ul>
          <li><strong>Essential cookies:</strong> Required for the service to function (authentication, session management);</li>
          <li><strong>Preference cookies:</strong> Remember your language and playback settings;</li>
          <li><strong>Analytics cookies:</strong> Help us understand how the service is used.</li>
        </ul>
        <p>You can control cookies through your browser settings. Disabling essential cookies may affect core functionality.</p>

        <h2>6. Data Retention</h2>
        <ul>
          <li><strong>Account data:</strong> Retained while your account is active and for up to 30 days after deletion;</li>
          <li><strong>Billing records:</strong> Retained for 7 years as required by tax and financial regulations;</li>
          <li><strong>Contact form messages:</strong> Retained for 2 years for support reference;</li>
          <li><strong>Usage data:</strong> Aggregated and anonymized after 12 months.</li>
        </ul>

        <h2>7. Your Rights</h2>
        <p>Depending on your location, you may have the right to:</p>
        <ul>
          <li>Access the personal data we hold about you;</li>
          <li>Request correction of inaccurate data;</li>
          <li>Request deletion of your data (subject to legal retention requirements);</li>
          <li>Request restriction or objection to processing;</li>
          <li>Request data portability;</li>
          <li>Withdraw consent at any time (without affecting prior processing);</li>
          <li>Lodge a complaint with your local data protection authority.</li>
        </ul>

        <h2>8. International Data Transfers</h2>
        <p>
          As a global service, your data may be processed in countries other than your own. We take
          appropriate measures to ensure data is protected in accordance with this Policy and applicable
          law, including Standard Contractual Clauses where required.
        </p>

        <h2>9. Security</h2>
        <p>
          We use industry-standard security measures including TLS encryption in transit, hashed
          passwords, and access controls. Payment data is tokenized and handled exclusively by Stripe.
          However, no method of transmission over the Internet is 100% secure.
        </p>

        <h2>10. Children's Privacy</h2>
        <p>
          Our service is not directed to children under 13 (or the minimum age in your jurisdiction).
          We do not knowingly collect data from children. If you believe a child has provided us data,
          please contact us and we will delete it promptly.
        </p>

        <h2>11. Changes to This Policy</h2>
        <p>
          We may update this Privacy Policy from time to time. We will notify you of significant changes
          by posting the updated policy on this page and updating the "Last updated" date.
        </p>

        <h2>12. Contact Us</h2>
        <p>
          If you have questions about this Privacy Policy or wish to exercise your data rights, please
          contact us:
        </p>
        <ul>
          <li>Email: <a href="mailto:trendameasor@mail.com">trendameasor@mail.com</a></li>
          <li>Phone: +852 6123 4567</li>
          <li>Address: {{ company }}., RM 1503-09, 15/F, Causeway Bay Centre, 15-23 Sugar St, Causeway Bay, Hong Kong</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from '../i18n'
const { t } = useI18n()
const brand = computed(() => t('brand.name'))
const company = computed(() => t('company.name'))
</script>

<style scoped>
.page-hero {
  padding: 64px 0 24px;
  text-align: center;
}
.page-title {
  font-size: clamp(30px, 5vw, 44px);
  font-weight: 900;
  letter-spacing: -0.02em;
  margin-bottom: 12px;
}
</style>
