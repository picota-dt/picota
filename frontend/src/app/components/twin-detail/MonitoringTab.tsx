import {useEffect, useMemo, useState} from "react";
import {DigitalTwin, useApp, Variable, VariableTelemetry} from "../../context/AppContext";
import {Line, LineChart, ResponsiveContainer, XAxis, YAxis,} from "recharts";
import {Activity, Cpu, Sparkles} from "lucide-react";

// ─── Variable Card ────────────────────────────────────────────────────────────

function VariableCard({variable, telemetry, loading}: {
    variable: Variable;
    telemetry?: VariableTelemetry;
    loading?: boolean
}) {
    const inferred = isInferredVariable(variable);
    const lastValueAt = useMemo(() => resolveTelemetryLastTimestampLabel(telemetry), [telemetry]);
    const history = useMemo(() => {
        if (!telemetry?.history?.length) return [];
        const normalized = telemetry.history
            .map((point) => ({
                time: formatTelemetryTime(point.time),
                value: Number(point.value),
                atMillis: parseTelemetryMillis(point.time) ?? Date.now(),
            }))
            .filter((point) => Number.isFinite(point.value));
        return normalized;
    }, [telemetry]);
    const current = useMemo(() => {
        if (Number.isFinite(telemetry?.current)) return Number(telemetry?.current);
        if (history.length === 0) return null;
        return history[history.length - 1]?.value ?? null;
    }, [history, telemetry]);
    const hasCurrent = current !== null && Number.isFinite(current);
    const min = history.length === 0 ? null : Math.min(...history.map((h) => h.value));
    const max = history.length === 0 ? null : Math.max(...history.map((h) => h.value));
    const yDomain = useMemo(() => {
        if (min === null || max === null) return [0, 1] as const;
        if (Math.abs(max - min) < 1e-9) {
            const delta = Math.abs(max) < 1 ? 1 : Math.abs(max) * 0.05;
            return [min - delta, max + delta] as const;
        }
        return [min * 0.95, max * 1.05] as const;
    }, [max, min]);
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
          {hasCurrent ? Number(current).toFixed(1) : ""}
        </span>
                {hasCurrent && <span className="text-white/35 text-xs mb-1">{variable.unit}</span>}
            </div>
            {lastValueAt && (
                <div className="text-white/30 text-[11px] font-mono mb-2">
                    on {lastValueAt}
                </div>
            )}

            {/* Sparkline */}
            <div className="h-14">
                {loading ? (
                    <div
                        className="h-full rounded-md border border-white/10 bg-black/20 flex items-center justify-center">
                        <span
                            className="w-4 h-4 rounded-full border-2 border-white/20 animate-spin"
                            style={{borderTopColor: color}}
                        />
                    </div>
                ) : history.length > 0 ? (
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
                            <YAxis domain={yDomain} tick={false} axisLine={false} tickLine={false}/>
                            <XAxis dataKey="time" hide/>
                        </LineChart>
                    </ResponsiveContainer>
                ) : (
                    <div
                        className="h-full rounded-md border border-white/10 bg-black/20 flex items-center justify-center text-[11px] text-white/30 font-mono">
                        No data
                    </div>
                )}
            </div>

            {min !== null && max !== null && (
                <div className="flex justify-between text-white/20 text-xs mt-1 font-mono">
                    <span>min {min.toFixed(1)}</span>
                    <span>max {max.toFixed(1)}</span>
                </div>
            )}
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
    const [loadedSubjects, setLoadedSubjects] = useState<Record<string, boolean>>({});

    useEffect(() => {
        let cancelled = false;
        setTelemetryBySubject({});
        setLoadedSubjects({});

        const refreshTelemetry = async () => {
            const entries = await Promise.all(
                twin.subjects.map(async (subject) => {
                    try {
                        const telemetry = await getSubjectTelemetry(twin.id, subject.id, 50);
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
            setLoadedSubjects((current) => {
                const next = {...current};
                for (const subject of twin.subjects) next[subject.id] = true;
                return next;
            });
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
                                loading={!loadedSubjects[s.id]}
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

function parseTelemetryMillis(value: unknown): number | null {
    if (value === null || value === undefined) return null;
    if (typeof value === "number" && Number.isFinite(value)) {
        const millis = Math.abs(value) < 1_000_000_000_000 ? value * 1000 : value;
        return Number.isFinite(millis) ? millis : null;
    }
    if (typeof value === "string") {
        const trimmed = value.trim();
        if (trimmed.length === 0) return null;
        const numeric = Number(trimmed);
        if (Number.isFinite(numeric)) return parseTelemetryMillis(numeric);
        const parsed = new Date(trimmed);
        if (Number.isNaN(parsed.getTime())) return null;
        return parsed.getTime();
    }
    return null;
}

function formatLastValueTimestamp(millis: number): string {
    const parsed = new Date(millis);
    if (Number.isNaN(parsed.getTime())) return "";
    return parsed.toLocaleString([], {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    });
}

function resolveTelemetryLastTimestampLabel(telemetry?: VariableTelemetry): string | null {
    if (!telemetry?.history?.length) return null;
    let latestMillis: number | null = null;
    for (const point of telemetry.history) {
        const millis = parseTelemetryMillis(point?.time);
        if (millis === null) continue;
        if (latestMillis === null || millis > latestMillis) {
            latestMillis = millis;
        }
    }
    if (latestMillis === null) return null;
    const formatted = formatLastValueTimestamp(latestMillis);
    return formatted.length > 0 ? formatted : null;
}
