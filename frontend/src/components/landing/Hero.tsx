import { forwardRef } from "react";
import { motion, MotionValue, useTransform } from "framer-motion";
import { ArrowRight, Sparkles } from "lucide-react";

interface HeroProps {
  robotOpacity: MotionValue<number>;
}

const Hero = forwardRef<HTMLDivElement, HeroProps>(({ robotOpacity }, ref) => {
  // Inverse opacity for the empty background
  const emptyOpacity = useTransform(robotOpacity, (v) => 1 - v);

  return (
    <section ref={ref} className="relative min-h-screen flex items-center pt-20 overflow-hidden" style={{ backgroundColor: '#fefcfe' }}>
      {/* Split background images - hidden on mobile */}
      <div className="absolute inset-0 hidden lg:block">
        {/* Left image - anchored to left edge */}
        <img
          src="/hero_left.png"
          alt=""
          className="absolute left-0 top-0 h-full w-auto object-contain object-left"
        />
        {/* Right image - anchored to right edge, with robot */}
        <motion.img
          src="/hero_right.png"
          alt=""
          className="absolute right-0 top-0 h-full w-auto object-contain object-right"
          style={{ opacity: robotOpacity }}
        />
        {/* Right image - anchored to right edge, without robot */}
        <motion.img
          src="/hero_right_empty.png"
          alt=""
          className="absolute right-0 top-0 h-full w-auto object-contain object-right"
          style={{ opacity: emptyOpacity }}
        />
      </div>
      {/* Fallback gradient for mobile */}
      <div className="absolute inset-0 lg:hidden mesh-gradient" />

      <div className="container mx-auto px-6 relative z-10">
        <div className="max-w-2xl lg:max-w-xl text-center mx-auto">
          {/* Badge */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="mb-6"
          >
            <span className="badge text-xs">
              <Sparkles className="w-3 h-3" />
              News Intelligence • AI-Powered
            </span>
          </motion.div>

          {/* Main headline */}
          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.1 }}
            className="font-body text-4xl md:text-5xl lg:text-4xl xl:text-5xl font-bold text-headline leading-[1.15] mb-5 tracking-tight"
          >
            AI that reads everything
            <span className="block mt-1">
              so <span className="gradient-text">you don't have to</span>
            </span>
          </motion.h1>

          {/* Subheadline */}
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.2 }}
            className="text-ink-light text-base lg:text-base xl:text-lg max-w-md mb-8 leading-relaxed mx-auto"
          >
            Morning Deck reads everything, scores what matters to you,
            and delivers personalized briefings—so you start each day informed, not overwhelmed.
          </motion.p>

          {/* CTA Buttons */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.3 }}
            className="flex flex-col sm:flex-row items-center justify-center gap-3 mb-8"
          >
            <a href="#cta" className="btn-primary flex items-center gap-2 group text-sm px-5 py-2.5">
              Join the waitlist
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </a>
            <a href="#how-it-works" className="btn-secondary text-sm px-5 py-2.5">
              See how it works
            </a>
          </motion.div>

          {/* Trust indicators */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.7, delay: 0.5 }}
            className="flex items-center justify-center gap-5 text-xs text-muted-foreground"
          >
            <span className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full bg-green-500" />
              Free beta access
            </span>
            <span className="flex items-center gap-2">
              <div className="w-2 h-2 rounded-full bg-green-500" />
              No credit card required
            </span>
          </motion.div>
        </div>
      </div>
    </section>
  );
});

Hero.displayName = "Hero";

export default Hero;
