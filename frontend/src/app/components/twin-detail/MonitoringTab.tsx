import {useEffect, useState} from "react";
import {DigitalTwin, DigitalSubject, Variable} from "../../context/AppContext";
import {
    LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from "recharts";
import {Activity, Cpu, Sparkles} from "lucide-react";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function jitter(value: number, pct = 0.05) {
    return +(value + value * (Math.random() - 0.5) * pct).toFixed(3);
}

function generateHistory(base: number, points = 20) {
    const history: { time: string; value: number }[] = [];
    let v = base;
    for (let i = points; i >= 0; i--) {
        const now = new Date(Date.now() - i * 3000);
        v = jitter(base, 0.08);
        history.push({
            time: now.toLocaleTimeString([], {hour: "2-digit", minute: "2-digit", second: "2-digit"}),
            value: v
        });
    }
    return history;
}

// ─── Variable Card ────────────────────────────────────────────────────────────

function VariableCard({variable}: { variable: Variable }) {
    const [history, setHistory] = useState(() => generateHistory(variable.value));
    const current = history[history.length - 1]?.value ?? variable.value;

    useEffect(() => {
        const interval = setInterval(() => {
            setHistory((prev) => {
                const now = new Date();
                const newPoint = {
                    time: now.toLocaleTimeString([], {hour: "2-digit", minute: "2-digit", second: "2-digit"}),
                    value: jitter(variable.value, 0.06),
                };
                return [...prev.slice(-19), newPoint];
            });
        }, 3000);
        return () => clearInterval(interval);
    }, [variable.value]);

    const min = Math.min(...history.map((h) => h.value));
    const max = Math.max(...history.map((h) => h.value));
    const color = variable.inferred ? "#a78bfa" : "#22d3ee";

    return (
        <div className="bg-[#0f1117] border border-white/8 rounded-xl p-4 hover:border-white/15 transition-colors">
            <div className="flex items-center justify-between mb-1">
                <div className="flex items-center gap-1.5">
                    {variable.inferred && <Sparkles className="w-3 h-3 text-violet-400"/>}
                    <span className="text-white/60 text-xs font-mono">{variable.name}</span>
                </div>
                <span
                    className={`text-xs px-1.5 py-0.5 rounded-md ${variable.inferred ? "bg-violet-500/15 text-violet-400" : "bg-cyan-500/15 text-cyan-400"}`}>
          {variable.inferred ? "inferred" : "sensor"}
        </span>
            </div>

            <div className="flex items-end gap-1 mb-3">
        <span className="text-white" style={{fontWeight: 700, fontSize: "1.4rem"}}>
          {current.toFixed(1)}
        </span>
                <span className="text-white/35 text-xs mb-1">{variable.unit}</span>
            </div>

            {/* Sparkline */}
            <div className="h-14">
                <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={history} margin={{top: 2, right: 2, left: -40, bottom: 0}}>
                        <Line
                            type="monotone"
                            dataKey="value"
                            stroke={color}
                            strokeWidth={1.5}
                            dot={false}
                            isAnimationActive={false}
                        />
                        <YAxis domain={[min * 0.95, max * 1.05]} tick={false} axisLine={false} tickLine={false}/>
                        <XAxis dataKey="time" hide/>
                    </LineChart>
                </ResponsiveContainer>
            </div>

            <div className="flex justify-between text-white/20 text-xs mt-1 font-mono">
                <span>min {min.toFixed(1)}</span>
                <span>max {max.toFixed(1)}</span>
            </div>
        </div>
    );
}

// ─── Subject Panel ────────────────────────────────────────────────────────────

function SubjectPanel({subject}: { subject: DigitalSubject }) {
    return (
        <div>
            <div className="flex items-center gap-2 mb-3">
                <div
                    className="w-6 h-6 rounded-lg bg-cyan-500/15 border border-cyan-500/20 flex items-center justify-center">
                    <Cpu className="w-3 h-3 text-cyan-400"/>
                </div>
                <h3 className="text-white text-sm" style={{fontWeight: 600}}>{subject.name}</h3>
                <span className="text-white/30 text-xs">{subject.variables.length} variables</span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {subject.variables.map((v) => (
                    <VariableCard key={v.id} variable={v}/>
                ))}
            </div>
        </div>
    );
}

// ─── Empty state ──────────────────────────────────────────────────────────────

function MonitoringEmpty() {
    return (
        <div className="flex flex-col items-center justify-center py-20 text-center">
            <div
                className="w-14 h-14 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-4">
                <Activity className="w-6 h-6 text-white/20"/>
            </div>
            <p className="text-white/40 mb-1" style={{fontWeight: 500}}>No subjects defined</p>
            <p className="text-white/20 text-sm max-w-xs">
                Add digital subjects and their variables in the Model tab to start monitoring.
            </p>
        </div>
    );
}

// ─── Main ─────────────────────────────────────────────────────────────────────

interface Props {
    twin: DigitalTwin;
}

export function MonitoringTab({twin}: Props) {
    if (twin.subjects.length === 0) return <MonitoringEmpty/>;

    return (
        <div className="flex flex-col gap-8">
            <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"/>
                <span className="text-emerald-400 text-sm" style={{fontWeight: 500}}>Live · updating every 3s</span>
            </div>
            {twin.subjects.map((s) => (
                <SubjectPanel key={s.id} subject={s}/>
            ))}
        </div>
    );
}
