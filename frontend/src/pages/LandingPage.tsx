import { useRef } from "react";
import { useScroll, useTransform } from "framer-motion";
import {
  Header,
  Hero,
  ProblemSection,
  SourcesSection,
  FeaturesSection,
  ProductPreview,
  PricingSection,
  CTASection,
  Footer,
} from "@/components/landing";

const LandingPage = () => {
  const heroRef = useRef<HTMLDivElement>(null);
  const problemRef = useRef<HTMLDivElement>(null);

  const { scrollYProgress: heroProgress } = useScroll({
    target: heroRef,
    offset: ["start start", "end start"],
  });

  const { scrollYProgress: problemProgress } = useScroll({
    target: problemRef,
    offset: ["start end", "end start"],
  });

  // Hero robot: fully visible until 40%, fades out 40-50%
  const heroRobotOpacity = useTransform(heroProgress, [0, 0.4, 0.5], [1, 1, 0]);

  // Problem robot arrival: invisible until 60%, fades in 60-70%
  const problemArrivalOpacity = useTransform(heroProgress, [0, 0.6, 0.7], [0, 0, 1]);
  // Problem robot departure: fully visible until 40%, fades out 40-50%
  const problemDepartureOpacity = useTransform(problemProgress, [0, 0.4, 0.5], [1, 1, 0]);

  // Sources robot: invisible until 60%, fades in 60-70%
  const sourcesRobotOpacity = useTransform(problemProgress, [0, 0.6, 0.7], [0, 0, 1]);

  return (
    <div className="landing-page min-h-screen bg-background">
      <Header />
      <main>
        <Hero ref={heroRef} robotOpacity={heroRobotOpacity} />
        <ProblemSection ref={problemRef} arrivalOpacity={problemArrivalOpacity} departureOpacity={problemDepartureOpacity} />
        <SourcesSection robotOpacity={sourcesRobotOpacity} />
        <FeaturesSection />
        <ProductPreview />
        <PricingSection />
        <CTASection />
      </main>
      <Footer />
    </div>
  );
};

export default LandingPage;
