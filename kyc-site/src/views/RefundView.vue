<template>
  <div class="legal-page">
    <section class="page-hero">
      <div class="container">
        <p class="kicker">Legal</p>
        <h1 class="page-title">Refund Policy</h1>
        <p class="legal-updated">Last updated: September 1, 2026</p>
      </div>
    </section>

    <section class="section">
      <div class="container legal-content">
        <p>
          This Refund Policy explains the terms under which {{ company }}. ("{{ brand }}",
          "we", or "us") issues refunds for digital content purchases, including VIP subscriptions
          and credit packs. By making a purchase, you agree to this policy.
        </p>

        <h2>1. Digital Content Nature</h2>
        <p>
          {{ brand }} provides digital streaming content. Due to the immediate, non-returnable nature
          of digital goods, unlocked episodes and consumed credits are generally
          <strong>non-refundable</strong> once accessed.
        </p>

        <h2>2. VIP Subscription Refunds</h2>
        <h3>2.1 Cancellation</h3>
        <ul>
          <li>You may cancel your subscription at any time from your account settings or by contacting support.</li>
          <li>Cancellation stops future renewals. Your access continues until the end of the current paid period.</li>
          <li>Cancelling does not automatically refund the current period unless a valid reason applies (see 2.2).</li>
        </ul>
        <h3>2.2 Refund Eligibility</h3>
        <p>You may be eligible for a subscription refund if:</p>
        <ul>
          <li>You request the refund within <strong>7 days</strong> of the charge;</li>
          <li>You have not streamed more than 2 episodes during the current billing period;</li>
          <li>The charge was accidental or unauthorized;</li>
          <li>There was a duplicate charge due to a billing error.</li>
        </ul>

        <h2>3. Credit Pack Refunds</h2>
        <ul>
          <li>Credit packs are refundable only if <strong>no credits have been used</strong> and the request is made within <strong>7 days</strong> of purchase.</li>
          <li>Once any credit from a pack is used to unlock an episode, the entire pack becomes non-refundable.</li>
          <li>Credits never expire, so there is no time pressure to use them — reducing the need for refunds.</li>
        </ul>

        <h2>4. How to Request a Refund</h2>
        <p>To request a refund, please contact us with the following information:</p>
        <ul>
          <li>Your account email address;</li>
          <li>The transaction date and amount;</li>
          <li>The reason for your refund request;</li>
          <li>Any relevant screenshots or details.</li>
        </ul>
        <p>
          Email: <a href="mailto:trendameasor@mail.com">trendameasor@mail.com</a><br />
          Phone: +852 6123 4567
        </p>
        <p>
          Our support team will review your request within <strong>2 business days</strong> and process
          approved refunds within <strong>5–10 business days</strong> to your original payment method.
        </p>

        <h2>5. Non-Refundable Cases</h2>
        <p>Refunds will not be issued in the following cases:</p>
        <ul>
          <li>The content was accessed and episodes were streamed after purchase;</li>
          <li>The refund request is made after the 7-day window;</li>
          <li>The account was terminated due to violation of our Terms of Service;</li>
          <li>Charges from more than 60 days ago (except for billing errors).</li>
        </ul>

        <h2>6. Chargebacks</h2>
        <p>
          We encourage you to contact us before initiating a chargeback with your bank or card issuer.
          Chargebacks filed without first attempting to resolve the issue with us may result in account
          suspension. We will promptly investigate any dispute and provide evidence to your bank as needed.
        </p>

        <h2>7. Statutory Consumer Rights</h2>
        <p>
          Nothing in this Refund Policy limits or excludes any statutory consumer rights you may have
          under applicable local laws. If you are protected by mandatory consumer protection laws in your
          jurisdiction, those rights remain fully applicable and are not affected by this policy.
        </p>

        <h2>8. Payment Method Specifics</h2>
        <ul>
          <li><strong>Credit/Debit Cards (via Stripe):</strong> Refunds are credited back to the original card.</li>
          <li><strong>Apple Pay / Google Pay:</strong> Refunds follow the respective platform's processing timeline.</li>
          <li>Processing times depend on your bank and may take up to 10 business days to appear.</li>
        </ul>

        <h2>9. Changes to This Policy</h2>
        <p>
          We reserve the right to update this Refund Policy at any time. Changes take effect upon posting
          the updated version on this page.
        </p>

        <h2>10. Contact Us</h2>
        <p>
          For any refund questions or to submit a request, please reach out:
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
