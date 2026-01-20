import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";
import { Check, Zap, Building2, Github } from "lucide-react";

const plans = [
  {
    name: "Free",
    icon: Github,
    monthlyPrice: "$0",
    annualPrice: "$0",
    period: "/forever",
    description: "Self-host the full application on your own machine.",
    features: [
      "Full source code access",
      "Self-hosted on your machine",
      "Community support via GitHub",
      "Works with any LLM provider",
      "Works with self-hosted models "
    ],
    cta: "Build it yourself",
    href: "https://github.com/eschnou/morningdeck",
    featured: false,
    isGithub: true,
  },
  {
    name: "Pro",
    icon: Zap,
    monthlyPrice: "€12",
    annualPrice: "€9",
    period: "/month",
    description: "For professionals who need to stay ahead of the curve.",
    features: [
      "10,000 items processed/month",
      "€1 per 1,000 items after",
      "Unlimited source integrations",
      "Daily briefings"
    ],
    cta: "Join the waitlist",
    href: "#cta",
    featured: true,
  },
  {
    name: "Business",
    icon: Building2,
    monthlyPrice: "Custom",
    annualPrice: "Custom",
    period: "pricing",
    description: "For teams that need collaboration and enterprise features.",
    features: [
      "Unlimited team members",
      "Shared briefings & workspaces",
      "SSO & advanced security",
      "Dedicated support",
      "Custom integrations",
      "SLA guarantees",
    ],
    cta: "Contact us",
    href: "mailto:contact@morningdeck.com",
    featured: false,
  },
];

const PricingSection = () => {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-100px" });
  const [isAnnual, setIsAnnual] = useState(true);

  return (
    <section ref={ref} id="pricing" className="py-24 bg-background relative">
      <div className="container mx-auto px-6">
        {/* Section header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6 }}
          className="text-center mb-16"
        >
          <span className="section-header">Pricing</span>
          <h2 className="font-body text-4xl md:text-5xl font-bold text-headline mt-4 mb-6 tracking-tight">
            Simple, transparent<br />
            <span className="gradient-text">pricing</span>
          </h2>
          <p className="text-ink-light text-lg max-w-2xl mx-auto">
            Try for free, upgrade when you're ready. No hidden fees, no surprises.
          </p>

          {/* Billing toggle */}
          <div className="flex items-center justify-center gap-4 mt-8">
            <span className={`text-sm font-medium ${!isAnnual ? 'text-headline' : 'text-muted-foreground'}`}>
              Monthly
            </span>
            <button
              onClick={() => setIsAnnual(!isAnnual)}
              className="relative w-14 h-7 bg-secondary rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-accent focus:ring-offset-2"
            >
              <span
                className={`absolute top-1 left-1 w-5 h-5 rounded-full bg-accent transition-transform ${
                  isAnnual ? 'translate-x-7' : 'translate-x-0'
                }`}
              />
            </button>
            <span className={`text-sm font-medium ${isAnnual ? 'text-headline' : 'text-muted-foreground'}`}>
              Annual
            </span>
            <span className="text-xs font-medium text-white bg-accent px-2 py-1 rounded-full">
              20% off
            </span>
          </div>
        </motion.div>

        {/* Pricing cards */}
        <div className="grid md:grid-cols-3 gap-6 max-w-5xl mx-auto">
          {plans.map((plan, index) => (
            <motion.div
              key={plan.name}
              initial={{ opacity: 0, y: 30 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.5, delay: index * 0.1 }}
              className={`relative bg-white border rounded-2xl p-8 flex flex-col transition-all duration-300 ${
                plan.featured
                  ? "border-accent/50 shadow-xl shadow-accent/10 scale-105 z-10"
                  : "border-border hover:border-border/80 hover:shadow-lg"
              }`}
            >
              {/* Featured badge */}
              {plan.featured && (
                <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                  <span className="bg-gradient-to-r from-accent to-purple-500 text-white text-xs font-medium px-4 py-1.5 rounded-full">
                    Most Popular
                  </span>
                </div>
              )}

              {/* Plan header */}
              <div className="mb-6">
                <div className="flex items-center gap-3 mb-4">
                  <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                    plan.featured ? "bg-accent/10" : "bg-secondary"
                  }`}>
                    <plan.icon className={`w-6 h-6 ${plan.featured ? "text-accent" : "text-ink"}`} />
                  </div>
                  <h3 className="font-body text-2xl font-bold text-headline">{plan.name}</h3>
                </div>

                <div className="flex items-baseline gap-1 mb-2">
                  <span className="font-body text-4xl font-bold text-headline">
                    {isAnnual ? plan.annualPrice : plan.monthlyPrice}
                  </span>
                  <span className="text-muted-foreground">{plan.period}</span>
                </div>

                <p className="text-muted-foreground text-sm">{plan.description}</p>
              </div>

              {/* Features list */}
              <ul className="space-y-3 mb-8 flex-1">
                {plan.features.map((feature) => (
                  <li key={feature} className="flex items-start gap-3 text-sm">
                    <div className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 ${
                      plan.featured ? "bg-accent/10" : "bg-secondary"
                    }`}>
                      <Check className={`w-3 h-3 ${
                        plan.featured ? "text-accent" : "text-ink"
                      }`} />
                    </div>
                    <span className="text-ink-light">{feature}</span>
                  </li>
                ))}
              </ul>

              {/* CTA button */}
              <a
                href={plan.href}
                className={`w-full rounded-lg py-3 font-medium transition-all text-center block flex items-center justify-center gap-2 ${
                  plan.featured
                    ? "btn-primary"
                    : "bg-secondary text-ink hover:bg-secondary/80"
                }`}
              >
                {plan.isGithub && <Github className="w-5 h-5" />}
                {plan.cta}
              </a>
            </motion.div>
          ))}
        </div>

        {/* Bottom note */}
        <motion.p
          initial={{ opacity: 0 }}
          animate={isInView ? { opacity: 1 } : {}}
          transition={{ duration: 0.6, delay: 0.5 }}
          className="text-center mt-12 text-muted-foreground text-sm"
        >
        </motion.p>
      </div>
    </section>
  );
};

export default PricingSection;
