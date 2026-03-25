import {useEffect, useMemo, useState} from "react";
import {DigitalTwin, useApp, Variable, VariableTelemetry} from "../../context/AppContext";
import {Line, LineChart, ResponsiveContainer, XAxis, YAxis,} from "recharts";
import {Activity, Cpu, Sparkles} from "lucide-react";

// ─── Variable Card ────────────────────────────────────────────────────────────

function VariableCard({variable, telemetry}: { variable: Variable; telemetry?: VariableTelemetry }) {
    const inferred = isInferredVariable(variable);
    const history = useMemo(() => {
        if (!telemetry?.history?.length) return fallbackHistory(variable.value);
        const normalized = telemetry.history
            .map((point) => ({
                time: formatTelemetryTime(point.time),
                value: Number(point.value),
            }))
            .filter((point) => Number.isFinite(point.value));
        return normalized.length > 0 ? normalized : fallbackHistory(variable.value);
    }, [telemetry, variable.value]);
    const current = Number.isFinite(telemetry?.current) ? Number(telemetry?.current) : (history[history.length - 1]?.value ?? variable.value);
    const min = Math.min(...history.map((h) => h.value));
    const max = Math.max(...history.map((h) => h.value));
    const color = inferred ? "#a78bfa" : "#22d3ee";

    return (
        <div className="bg-[#0f1117] border border-white/8 rounded-xl p-4 hover:border-white/15 transition-colors">
            <div className="flex items-center justify-between mb-1">
                <div className="flex items-center gap-1.5">
                    {inferred && <Sparkles className="w-3 h-3 text-violet-400"/>}
                    <span className="text-white/60 text-xs font-mono">{variable.name}</span>
                </div>
                <span
                    className={`text-xs px-1.5 py-0.5 rounded-md ${inferred ? "bg-violet-500/15 text-violet-400" : "bg-cyan-500/15 text-cyan-400"}`}>
          {inferred ? "inferred" : "sensor"}
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
    const {getSubjectTelemetry} = useApp();
    const [telemetryBySubject, setTelemetryBySubject] = useState<Record<string, Record<string, VariableTelemetry>>>({});

    useEffect(() => {
        let cancelled = false;

        const refreshTelemetry = async () => {
            const entries = await Promise.all(
                twin.subjects.map(async (subject) => {
                    try {
                        const telemetry = await getSubjectTelemetry(twin.id, subject.id, 20);
                        const byVariable = Object.fromEntries(
                            telemetry.map((item) => [item.variableId, item])
                        );
                        return [subject.id, byVariable] as const;
                    } catch {
                        return [subject.id, {} as Record<string, VariableTelemetry>] as const;
                    }
                })
            );
            if (cancelled) return;
            setTelemetryBySubject(Object.fromEntries(entries));
        };

        refreshTelemetry();
        const interval = window.setInterval(refreshTelemetry, 3000);
        return () => {
            cancelled = true;
            window.clearInterval(interval);
        };
    }, [twin.id, twin.subjects]);

    if (twin.subjects.length === 0) return <MonitoringEmpty/>;

    return (
        <div className="flex flex-col gap-8">
            <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"/>
                <span className="text-emerald-400 text-sm" style={{fontWeight: 500}}>Live · updating every 3s</span>
            </div>
            {twin.subjects.map((s) => (
                <div key={s.id}>
                    <div className="flex items-center gap-2 mb-3">
                        <div
                            className="w-6 h-6 rounded-lg bg-cyan-500/15 border border-cyan-500/20 flex items-center justify-center">
                            <Cpu className="w-3 h-3 text-cyan-400"/>
                        </div>
                        <h3 className="text-white text-sm" style={{fontWeight: 600}}>{s.name}</h3>
                        <span className="text-white/30 text-xs">{s.variables.length} variables</span>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                        {monitoringTilesForSubject(s.variables).map((v) => (
                            <VariableCard
                                key={v.id}
                                variable={v}
                                telemetry={telemetryBySubject[s.id]?.[v.id]}
                            />
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
}

function monitoringTilesForSubject(variables: Variable[]): Variable[] {
    const safeVariables = Array.isArray(variables) ? variables.filter((value): value is Variable => !!value) : [];
    const tiles: Variable[] = [];
    for (const variable of safeVariables) {
        if (isInferredVariable(variable) && !hasSensorCounterpart(safeVariables, variable)) {
            const sensorTileId = sensorActualTileId(variable);
            tiles.push({
                ...variable,
                id: sensorTileId,
                name: `${variable.name} (actual)`,
                inferred: false,
                variableType: "sensor",
            });
        }
        if (isInferredVariable(variable)) {
            tiles.push({
                ...variable,
                id: inferredTileId(variable),
                name: inferredTileDisplayName(variable),
            });
            continue;
        }
        tiles.push(variable);
    }
    return tiles;
}

function hasSensorCounterpart(variables: Variable[], inferredVariable: Variable): boolean {
    const inferredId = normalizeTelemetryKey(inferredVariable.id);
    const inferredName = normalizeTelemetryKey(inferredVariable.name);
    return variables.some((candidate) => {
        if (!candidate || isInferredVariable(candidate)) return false;
        const candidateId = normalizeTelemetryKey(candidate.id);
        const candidateName = normalizeTelemetryKey(candidate.name);
        if (inferredId && candidateId && inferredId === candidateId) return true;
        if (inferredName && candidateName && inferredName === candidateName) return true;
        return false;
    });
}

function sensorActualTileId(variable: Variable): string {
    const base = telemetryIdentityBase(variable);
    return `${base}__sensor_actual`;
}

function inferredTileId(variable: Variable): string {
    const base = telemetryIdentityBase(variable);
    return `${base}__inferred`;
}

function inferredTileDisplayName(variable: Variable): string {
    const horizon = typeof variable.timeHorizon === "number" && Number.isFinite(variable.timeHorizon)
        ? Math.max(0, Math.trunc(variable.timeHorizon))
        : 0;
    return horizon > 0 ? `${variable.name} · t+${horizon}` : variable.name;
}

function normalizeTelemetryKey(value: string | undefined): string {
    return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function telemetryIdentityBase(variable: Variable): string {
    const byId = typeof variable.id === "string" ? variable.id.trim() : "";
    if (byId.length > 0) return byId;
    const byName = typeof variable.name === "string" ? variable.name.trim() : "";
    return byName.length > 0 ? byName : "variable";
}

function isInferredVariable(variable: Variable): boolean {
    if (typeof variable.inferred === "boolean") return variable.inferred;
    return variable.variableType === "inferred";
}

function formatTelemetryTime(value: string): string {
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return value;
    return parsed.toLocaleTimeString([], {hour: "2-digit", minute: "2-digit", second: "2-digit"});
}

function fallbackHistory(base: number, points = 20) {
    const safeBase = Number.isFinite(base) ? base : 0;
    const history: { time: string; value: number }[] = [];
    for (let i = points; i >= 0; i--) {
        const now = new Date(Date.now() - i * 3000);
        history.push({
            time: now.toLocaleTimeString([], {hour: "2-digit", minute: "2-digit", second: "2-digit"}),
            value: safeBase,
        });
    }
    return history;
}
