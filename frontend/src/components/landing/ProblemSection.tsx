import { forwardRef } from "react";
import { motion, MotionValue, useInView, useTransform } from "framer-motion";
import { useRef } from "react";
import { Inbox, Puzzle, Clock, Brain } from "lucide-react";

interface ProblemSectionProps {
  arrivalOpacity: MotionValue<number>;
  departureOpacity: MotionValue<number>;
}

const problems = [
  {
    icon: Inbox,
    title: "Information Overload",
    description: "Hundreds of articles, newsletters, and feeds—but no way to prioritize what actually matters to you.",
  },
  {
    icon: Puzzle,
    title: "Fragmented Sources",
    description: "Switching between apps, tabs, and subscriptions. Your attention scattered across a dozen platforms.",
  },
  {
    icon: Clock,
    title: "Time Drain",
    description: "Hours lost to scanning headlines instead of understanding what moves your industry forward.",
  },
  {
    icon: Brain,
    title: "No Memory",
    description: "That article you read last month? Gone. No way to search, retrieve, or build on past insights.",
  },
];

const ProblemSection = forwardRef<HTMLDivElement, ProblemSectionProps>(({ arrivalOpacity, departureOpacity }, ref) => {
  const inViewRef = useRef(null);
  const isInView = useInView(inViewRef, { once: true, margin: "-100px" });

  // Combine arrival and departure: visible only when arrived AND not left
  const robotOpacity = useTransform(
    [arrivalOpacity, departureOpacity],
    ([arrival, departure]) => (arrival as number) * (departure as number)
  );

  return (
    <section ref={ref} className="py-24 bg-surface relative overflow-hidden">
      {/* Robot image on the left - hidden on mobile */}
      <motion.img
        src="/problem_left.png"
        alt=""
        className="absolute left-0 top-8 h-2/3 w-auto object-contain object-left hidden lg:block"
        style={{ opacity: robotOpacity }}
      />

      <div ref={inViewRef} className="container mx-auto px-6">
        {/* Section header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6 }}
          className="text-center mb-16"
        >
          <span className="section-label">The Problem</span>
          <h2 className="font-body text-4xl md:text-5xl font-bold text-headline mt-6 mb-6 tracking-tight">
            The news never stops.<br />
            <span className="text-ink-light">But your time does.</span>
          </h2>
        </motion.div>

        {/* Problems grid */}
        <div className="grid md:grid-cols-2 gap-6 max-w-4xl mx-auto">
          {problems.map((problem, index) => (
            <motion.div
              key={problem.title}
              initial={{ opacity: 0, y: 20 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.5, delay: index * 0.1 }}
              className="surface-card p-8 group"
            >
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl bg-secondary flex items-center justify-center shrink-0 group-hover:bg-accent/10 transition-colors">
                  <problem.icon className="w-6 h-6 text-ink group-hover:text-accent transition-colors" />
                </div>
                <div>
                  <h3 className="font-body text-xl font-semibold text-headline mb-2">{problem.title}</h3>
                  <p className="text-ink-light text-sm leading-relaxed">{problem.description}</p>
                </div>
              </div>
            </motion.div>
          ))}
        </div>

        {/* Modern quote */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={isInView ? { opacity: 1 } : {}}
          transition={{ duration: 0.8, delay: 0.5 }}
          className="max-w-3xl mx-auto mt-20 text-center"
        >
          <blockquote className="quote-modern">
            "I subscribe to 12 newsletters, follow 50 accounts, and still miss what matters."
          </blockquote>
          <cite className="block mt-4 text-sm text-muted-foreground not-italic">
            — Every knowledge worker, everywhere
          </cite>
        </motion.div>
      </div>
    </section>
  );
});

ProblemSection.displayName = "ProblemSection";

export default ProblemSection;
