import { motion, MotionValue, useInView } from "framer-motion";
import { useRef } from "react";
import { Rss, Globe, Mail, MessageSquare, Hash } from "lucide-react";

interface SourcesSectionProps {
  robotOpacity: MotionValue<number>;
}

const sources = [
  { icon: Rss, name: "RSS Feeds", description: "Any RSS or Atom feed" },
  { icon: Globe, name: "Websites", description: "Blogs, news sites, portfolios" },
  { icon: Mail, name: "Newsletters", description: "Substack, Beehiiv, Ghost" },
  { icon: MessageSquare, name: "Reddit", description: "Subreddits & discussions" },
  { icon: Hash, name: "Custom APIs", description: "Any JSON endpoint" },
];

const SourcesSection = ({ robotOpacity }: SourcesSectionProps) => {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-100px" });

  return (
    <section ref={ref} className="py-24 bg-surface relative overflow-hidden">
      {/* Subtle dot pattern */}
      <div className="absolute inset-0 dot-pattern opacity-50" />

      {/* Robot image on the right - hidden on mobile */}
      <motion.img
        src="/source_right.png"
        alt=""
        className="absolute right-0 top-8 h-2/3 w-auto object-contain object-right hidden lg:block"
        style={{ opacity: robotOpacity }}
      />

      <div className="container mx-auto px-6 relative">
        {/* Section header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6 }}
          className="text-center mb-16"
        >
          <span className="section-header">Universal Coverage</span>
          <h2 className="font-body text-4xl md:text-5xl font-bold text-headline mt-4 mb-6 tracking-tight">
            Every source.<br />
            <span className="gradient-text">One inbox.</span>
          </h2>
          <p className="text-ink-light text-lg max-w-2xl mx-auto">
            Connect anything that publishes content. We normalize, process, and score it all—so you never miss what matters.
          </p>
        </motion.div>

        {/* Sources grid */}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4 max-w-5xl mx-auto">
          {sources.map((source, index) => (
            <motion.div
              key={source.name}
              initial={{ opacity: 0, y: 20 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.4, delay: index * 0.05 }}
              className="group relative surface-card p-6 hover:border-accent/30"
            >
              {/* Icon */}
              <div className="w-12 h-12 rounded-xl bg-secondary flex items-center justify-center mb-4 group-hover:bg-accent/10 transition-colors">
                <source.icon className="w-6 h-6 text-ink group-hover:text-accent transition-colors" />
              </div>

              {/* Text */}
              <h3 className="font-body text-lg font-semibold text-headline mb-1">{source.name}</h3>
              <p className="text-muted-foreground text-sm leading-snug">{source.description}</p>

              {/* Hover gradient line */}
              <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-accent to-purple-500 scale-x-0 group-hover:scale-x-100 transition-transform origin-left rounded-full" />
            </motion.div>
          ))}
        </div>

        {/* Bottom tagline */}
        <motion.p
          initial={{ opacity: 0 }}
          animate={isInView ? { opacity: 1 } : {}}
          transition={{ duration: 0.6, delay: 0.6 }}
          className="text-center mt-12 text-muted-foreground"
        >
          …and we're adding more every week
        </motion.p>
      </div>
    </section>
  );
};

export default SourcesSection;
