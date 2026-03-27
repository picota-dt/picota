import {useNavigate} from "react-router";
import {Activity, ArrowRight, ChevronRight, Layers, ShieldCheck, Zap} from "lucide-react";

const FEATURES = [
    {
        icon: Layers,
        title: "Model any asset",
        description: "Define digital subjects, variables and constraints using a simple declarative DSL.",
    },
    {
        icon: Activity,
        title: "Live monitoring",
        description: "Stream real-time sensor data into your twin and visualize every variable as it changes.",
    },
    {
        icon: Zap,
        title: "AI inference engine",
        description: "Train physics-informed neural networks to infer unmeasured variables from observable ones.",
    },
    {
        icon: ShieldCheck,
        title: "Constraint validation",
        description: "Define operational constraints and get alerted when the system approaches violation.",
    },
];

const STATS = [
    {label: "Twins created", value: "12,400+"},
    {label: "Variables monitored", value: "2.1M"},
    {label: "Inference accuracy", value: "93.4%"},
    {label: "Uptime", value: "99.9%"},
];

export default function LandingPage() {
    const navigate = useNavigate();

    return (
        <div className="min-h-screen bg-[#0f1117] text-white flex flex-col">
            {/* Navbar */}
            <header className="border-b border-white/6 sticky top-0 z-50 bg-[#0f1117]/90 backdrop-blur-md">
                <div className="max-w-6xl mx-auto px-4 sm:px-6 h-14 flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                        <div
                            className="w-7 h-7 rounded-lg bg-cyan-500 flex items-center justify-center shadow-lg shadow-cyan-500/30">
                            <img src="/icons/icon-512.png" alt="Picota" className="w-4 h-4 object-contain"/>
                        </div>
                        <span className="text-white" style={{fontWeight: 700, fontSize: "1rem"}}>Picota</span>
                    </div>
                    <div className="flex items-center gap-3">
                        <button
                            onClick={() => navigate("/login")}
                            className="text-white/50 hover:text-white text-sm transition-colors"
                        >
                            Continue with Google
                        </button>
                        <button
                            onClick={() => navigate("/login")}
                            className="bg-cyan-500 hover:bg-cyan-400 text-white text-sm px-4 py-2 rounded-xl transition-colors shadow-lg shadow-cyan-500/20"
                            style={{fontWeight: 500}}
                        >
                            Get started
                        </button>
                    </div>
                </div>
            </header>

            {/* Hero */}
            <section
                className="flex-1 flex flex-col items-center justify-center text-center px-4 py-24 relative overflow-hidden">
                {/* Background glow */}
                <div
                    className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-cyan-500/8 rounded-full blur-3xl pointer-events-none"/>
                <div
                    className="absolute top-1/3 left-1/3 w-[300px] h-[300px] bg-blue-600/6 rounded-full blur-3xl pointer-events-none"/>

                <div className="relative z-10 max-w-3xl mx-auto">
                    <div
                        className="inline-flex items-center gap-2 bg-cyan-500/10 border border-cyan-500/20 rounded-full px-3.5 py-1.5 mb-6">
                        <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse"/>
                        <span className="text-cyan-400 text-xs" style={{fontWeight: 500}}>Now in public beta</span>
                    </div>

                    <h1 style={{fontWeight: 800, fontSize: "clamp(2.2rem, 6vw, 3.8rem)", lineHeight: 1.1}}
                        className="text-white mb-5">
                        Digital twins,{" "}
                        <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-blue-400">
              minus the complexity
            </span>
                    </h1>

                    <p className="text-white/50 text-lg max-w-xl mx-auto mb-8" style={{lineHeight: 1.7}}>
                        Picota lets you model, monitor and infer the behavior of any physical system — from a single
                        pump to an entire factory floor.
                    </p>

                    <div className="flex items-center justify-center gap-3 flex-wrap">
                        <button
                            onClick={() => navigate("/login")}
                            className="flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 text-white px-6 py-3 rounded-xl transition-all shadow-xl shadow-cyan-500/25"
                            style={{fontWeight: 600}}
                        >
                            Continue with Google
                            <ArrowRight className="w-4 h-4"/>
                        </button>
                        <button
                            onClick={() => navigate("/login")}
                            className="flex items-center gap-2 bg-white/6 hover:bg-white/10 text-white/70 hover:text-white px-6 py-3 rounded-xl border border-white/10 transition-all"
                        >
                            Access Picota
                            <ChevronRight className="w-4 h-4"/>
                        </button>
                    </div>
                </div>
            </section>

            {/* Stats */}
            <section className="border-y border-white/6 py-10">
                <div className="max-w-4xl mx-auto px-4 grid grid-cols-2 sm:grid-cols-4 gap-6">
                    {STATS.map((s) => (
                        <div key={s.label} className="text-center">
                            <p className="text-white" style={{fontWeight: 700, fontSize: "1.6rem"}}>{s.value}</p>
                            <p className="text-white/35 text-sm mt-1">{s.label}</p>
                        </div>
                    ))}
                </div>
            </section>

            {/* Features */}
            <section className="py-20 px-4">
                <div className="max-w-5xl mx-auto">
                    <p className="text-center text-white/30 text-xs uppercase tracking-widest mb-3">Platform</p>
                    <h2 className="text-center text-white mb-12"
                        style={{fontWeight: 700, fontSize: "clamp(1.4rem, 3vw, 2rem)"}}>
                        Everything you need to build a digital twin
                    </h2>
                    <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {FEATURES.map(({icon: Icon, title, description}) => (
                            <div
                                key={title}
                                className="bg-white/3 border border-white/8 rounded-2xl p-5 hover:border-cyan-500/30 transition-all group"
                            >
                                <div
                                    className="w-9 h-9 rounded-xl bg-cyan-500/15 border border-cyan-500/20 flex items-center justify-center mb-4 group-hover:bg-cyan-500/25 transition-colors">
                                    <Icon className="w-4 h-4 text-cyan-400"/>
                                </div>
                                <h3 className="text-white mb-2" style={{fontWeight: 600}}>{title}</h3>
                                <p className="text-white/40 text-sm" style={{lineHeight: 1.6}}>{description}</p>
                            </div>
                        ))}
                    </div>
                </div>
            </section>

            {/* CTA */}
            <section className="py-16 px-4 text-center">
                <div
                    className="max-w-xl mx-auto bg-gradient-to-br from-cyan-500/10 to-blue-600/10 border border-cyan-500/20 rounded-3xl p-10">
                    <h2 className="text-white mb-3" style={{fontWeight: 700, fontSize: "1.5rem"}}>
                        Ready to create your first twin?
                    </h2>
                    <p className="text-white/40 text-sm mb-6">
                        Get started in minutes. No infrastructure required.
                    </p>
                    <button
                        onClick={() => navigate("/login")}
                        className="bg-cyan-500 hover:bg-cyan-400 text-white px-6 py-3 rounded-xl transition-colors shadow-lg shadow-cyan-500/20"
                        style={{fontWeight: 600}}
                    >
                        Continue with Google
                    </button>
                </div>
            </section>

            {/* Footer */}
            <footer className="border-t border-white/6 py-6 text-center">
                <p className="text-white/20 text-xs">© 2026 Picota. All rights reserved.</p>
            </footer>
        </div>
    );
}
