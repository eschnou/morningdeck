import { motion, useInView } from "framer-motion";
import { useRef } from "react";
import { BookOpen, Brain, FileText, Share2 } from "lucide-react";

const features = [
  {
    icon: BookOpen,
    number: "01",
    title: "Reads for you",
    description: "Monitors RSS feeds, newsletters, Twitter, and more—24/7. Hundreds of sources, processed while you sleep.",
    detail: "Add any source: RSS, newsletters, Twitter lists, Reddit, HN. We pull and process everything.",
  },
  {
    icon: Brain,
    number: "02",
    title: "Thinks like you",
    description: "Define your interests, industry, and criteria. Our AI scores every piece of content against your persona.",
    detail: "Relevance scoring from 0-100. Your priorities, your weights, your definition of 'important'.",
  },
  {
    icon: FileText,
    number: "03",
    title: "Briefs you",
    description: "Wake up to a curated daily briefing. The top stories, summarized and organized—ready in minutes.",
    detail: "Choose your format: email digest, in-app reading, web or mobile.",
  },
  {
    icon: Share2,
    number: "04",
    title: "Shares for you",
    description: "Share your curated feeds with your team or community. Collaborate on what matters most.",
    detail: "Export briefings, share source collections, or publish curated feeds for others to follow.",
  },
];

const FeaturesSection = () => {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-100px" });

  return (
    <section ref={ref} id="features" className="py-24 bg-background relative">
      <div className="container mx-auto px-6">
        {/* Section header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6 }}
          className="text-center mb-16"
        >
          <span className="section-header">How It Works</span>
          <h2 className="font-body text-3xl md:text-4xl font-bold text-headline mt-4 mb-4 tracking-tight">
            From chaos to clarity, <span className="gradient-text">automatically</span>
          </h2>
        </motion.div>

        {/* Features grid */}
        <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-8 max-w-6xl mx-auto">
          {features.map((feature, index) => (
            <motion.div
              key={feature.title}
              initial={{ opacity: 0, y: 20 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.5, delay: index * 0.1 }}
              className="relative group"
            >
              {/* Number badge */}
              <span className="font-body text-6xl font-bold text-secondary absolute -top-4 -left-2 group-hover:text-accent/20 transition-colors">{feature.number}</span>

              {/* Card content */}
              <div className="pt-8 pl-4 relative">
                <div className="w-12 h-12 rounded-xl bg-secondary flex items-center justify-center mb-4 group-hover:bg-accent/10 transition-colors">
                  <feature.icon className="w-6 h-6 text-ink group-hover:text-accent transition-colors" />
                </div>
                <h3 className="font-body text-xl font-semibold text-headline mb-3">{feature.title}</h3>
                <p className="text-ink-light text-sm leading-relaxed mb-3">{feature.description}</p>
                <p className="text-muted-foreground text-xs bg-secondary rounded-lg p-3">{feature.detail}</p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default FeaturesSection;
