<template>
  <div class="legal-page">
    <section class="page-hero">
      <div class="container">
        <p class="kicker">Legal</p>
        <h1 class="page-title">Terms of Service</h1>
        <p class="legal-updated">Last updated: September 1, 2026</p>
      </div>
    </section>

    <section class="section">
      <div class="container legal-content">
        <p>
          These Terms of Service ("Terms") govern your use of the {{ brand }} streaming platform
          (the "Service") operated by {{ company }}. ("{{ brand }}", "we", "us", or "our").
          By accessing or using the Service, you agree to be bound by these Terms. If you do not agree,
          please do not use the Service.
        </p>

        <h2>1. Definitions</h2>
        <ul>
          <li><strong>"Service"</strong> refers to the {{ brand }} website, applications, and all related streaming content.</li>
          <li><strong>"Content"</strong> refers to all short dramas, episodes, videos, and related media available on the Service.</li>
          <li><strong>"Credits"</strong> refers to the virtual units used to unlock individual episodes.</li>
          <li><strong>"VIP Membership"</strong> refers to the subscription that grants unlimited streaming access.</li>
          <li><strong>"User"</strong> refers to any individual who accesses or uses the Service.</li>
        </ul>

        <h2>2. Eligibility and Accounts</h2>
        <ul>
          <li>You must be at least 13 years old (or the minimum age in your jurisdiction) to use the Service.</li>
          <li>To purchase subscriptions or credits, you must be the age of majority in your jurisdiction and capable of entering a binding contract.</li>
          <li>You are responsible for maintaining the confidentiality of your account credentials and for all activity under your account.</li>
          <li>You agree to provide accurate information and update it when necessary.</li>
        </ul>

        <h2>3. Subscriptions and Payments</h2>
        <h3>3.1 VIP Membership</h3>
        <ul>
          <li>Subscriptions are billed in advance on a monthly, quarterly, or annual basis in USD.</li>
          <li>Subscriptions auto-renew at the end of each billing cycle unless cancelled before the renewal date.</li>
          <li>You may cancel at any time; access continues until the period ends.</li>
        </ul>
        <h3>3.2 Credit Packs</h3>
        <ul>
          <li>Credit packs are one-time purchases in USD.</li>
          <li>Credits are applied to your account and never expire.</li>
          <li>Credits have no cash value and are non-transferable between accounts.</li>
        </ul>
        <h3>3.3 Payment</h3>
        <ul>
          <li>All payments are processed by Stripe. We do not store your card details.</li>
          <li>You authorize us to charge the stated amounts through your selected payment method.</li>
          <li>Taxes, where applicable, are added at checkout based on your billing location.</li>
        </ul>

        <h2>4. Content License</h2>
        <p>
          All Content is licensed to {{ brand }} by its respective rights holders. By accessing Content,
          you receive a limited, non-exclusive, non-transferable, revocable license to stream the
          Content for <strong>personal, non-commercial use only</strong>.
        </p>
        <p>You agree that you will not:</p>
        <ul>
          <li>Download, record, copy, or redistribute Content;</li>
          <li>Stream Content publicly or charge others for access;</li>
          <li>Modify, reverse-engineer, or create derivative works;</li>
          <li>Use automated tools to scrape or extract Content;</li>
          <li>Circumvent any digital rights management or access controls.</li>
        </ul>

        <h2>5. Acceptable Use</h2>
        <p>You agree not to:</p>
        <ul>
          <li>Use the Service for any unlawful purpose;</li>
          <li>Harass, abuse, or harm other users;</li>
          <li>Upload or transmit viruses, malware, or harmful code;</li>
          <li>Interfere with the Service's security or operation;</li>
          <li>Share or sell your account credentials;</li>
          <li>Attempt to gain unauthorized access to our systems.</li>
        </ul>

        <h2>6. Intellectual Property</h2>
        <p>
          The Service, including its design, text, graphics, logos, and software, is the property of
          {{ brand }} or its licensors and is protected by intellectual property laws. Unauthorized use
          is prohibited.
        </p>

        <h2>7. User-Generated Content</h2>
        <p>
          If the Service allows comments or other user submissions, you grant {{ brand }} a
          non-exclusive, royalty-free license to use, display, and reproduce your content within the
          Service. You are solely responsible for your submissions and must not infringe others' rights.
        </p>

        <h2>8. Disclaimers</h2>
        <p>
          The Service is provided on an "as is" and "as available" basis. To the fullest extent
          permitted by law, {{ brand }} disclaims all warranties, express or implied, including
          merchantability, fitness for a particular purpose, and non-infringement. We do not warrant
          that the Service will be uninterrupted, error-free, or secure.
        </p>

        <h2>9. Limitation of Liability</h2>
        <p>
          To the maximum extent permitted by law, {{ brand }} shall not be liable for any indirect,
          incidental, special, consequential, or punitive damages, including loss of profits, data, or
          goodwill, arising from your use of the Service. Our total liability for any claim shall not
          exceed the amount you paid us in the 12 months preceding the claim.
        </p>

        <h2>10. Indemnification</h2>
        <p>
          You agree to indemnify and hold harmless {{ brand }}, its officers, directors, and employees
          from any claims, damages, or expenses arising from your use of the Service or your violation
          of these Terms.
        </p>

        <h2>11. Suspension and Termination</h2>
        <p>
          We may suspend or terminate your access to the Service at any time, with or without cause,
          including for violation of these Terms. Upon termination, your right to use the Service and
          access Content ceases immediately.
        </p>

        <h2>12. Governing Law</h2>
        <p>
          These Terms are governed by the laws of the Hong Kong Special Administrative Region, without regard to conflict
          of law principles. Any disputes shall be subject to the exclusive jurisdiction of the courts
          of Singapore.
        </p>

        <h2>13. Changes to These Terms</h2>
        <p>
          We may modify these Terms at any time. Material changes will be communicated via the Service
          or by email. Continued use of the Service after changes take effect constitutes acceptance of
          the updated Terms.
        </p>

        <h2>14. Contact</h2>
        <p>For questions about these Terms, please contact us:</p>
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
