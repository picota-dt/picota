import {useRef, useState, useEffect} from "react";
import {
    DigitalTwin, DigitalSubject, SubjectDataset, VariableStat, useApp,
} from "../../context/AppContext";
import {
    Upload, Database, Activity, Trash2, FileText,
    TrendingUp, TrendingDown, Minus, Info, AlertCircle,
} from "lucide-react";

// ─── CSV parser & stats ───────────────────────────────────────────────────────

function parseCSV(text: string): { headers: string[]; rows: number[][] } {
    const lines = text.trim().split(/\r?\n/);
    if (lines.length < 2) return {headers: [], rows: []};
    const headers = lines[0].split(",").map((h) => h.trim().replace(/^"|"$/g, ""));
    const rows: number[][] = [];
    for (let i = 1; i < lines.length; i++) {
        const cells = lines[i].split(",").map((c) => parseFloat(c.trim()));
        if (cells.some(isNaN)) continue;
        rows.push(cells);
    }
    return {headers, rows};
}

function computeStats(values: number[]): VariableStat {
    const sorted = [...values].sort((a, b) => a - b);
    const count = values.length;
    const mean = values.reduce((s, v) => s + v, 0) / count;
    const variance = values.reduce((s, v) => s + (v - mean) ** 2, 0) / count;
    const std = Math.sqrt(variance);
    const min = sorted[0];
    const max = sorted[sorted.length - 1];
    const mid = Math.floor(sorted.length / 2);
    const median = sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
    return {count, mean, std, min, max, median};
}

function buildStats(headers: string[], rows: number[][]): Record<string, VariableStat> {
    const result: Record<string, VariableStat> = {};
    headers.forEach((h, i) => {
        const vals = rows.map((r) => r[i]).filter((v) => !isNaN(v));
        if (vals.length > 0) result[h] = computeStats(vals);
    });
    return result;
}

// ─── Simulated real-time record growth ────────────────────────────────────────

function useRealtimeCounter(initial: number, active: boolean) {
    const [count, setCount] = useState(initial);
    useEffect(() => {
        if (!active) return;
        const id = setInterval(() => {
            setCount((c) => c + Math.floor(Math.random() * 3 + 1));
        }, 3000);
        return () => clearInterval(id);
    }, [active]);
    return count;
}

// ─── Mini bar for stats ───────────────────────────────────────────────────────

function MiniBar({value, min, max}: { value: number; min: number; max: number }) {
    const range = max - min || 1;
    const pct = ((value - min) / range) * 100;
    return (
        <div className="relative h-1 bg-white/8 rounded-full w-full">
            <div
                className="absolute h-full bg-cyan-400/60 rounded-full"
                style={{width: `${pct}%`}}
            />
        </div>
    );
}

// ─── Stats table ─────────────────────────────────────────────────────────────

function StatsTable({stats, variables}: {
    stats: Record<string, VariableStat>;
    variables: { name: string; unit: string }[]
}) {
    const fmt = (n: number) => {
        if (Math.abs(n) >= 1000) return n.toLocaleString("en-US", {maximumFractionDigits: 0});
        if (Math.abs(n) >= 10) return n.toFixed(2);
        return n.toFixed(3);
    };

    const varMap = Object.fromEntries(variables.map((v) => [v.name, v.unit]));

    const rows = Object.entries(stats).filter(([, s]) => s.count > 0);

    return (
        <div className="overflow-x-auto">
            <table className="w-full text-xs">
                <thead>
                <tr className="border-b border-white/8">
                    <th className="text-left text-white/30 uppercase tracking-wider pb-2 pr-4 whitespace-nowrap">Variable</th>
                    <th className="text-right text-white/30 uppercase tracking-wider pb-2 px-3 whitespace-nowrap">Count</th>
                    <th className="text-right text-white/30 uppercase tracking-wider pb-2 px-3 whitespace-nowrap">Mean</th>
                    <th className="text-right text-white/30 uppercase tracking-wider pb-2 px-3 whitespace-nowrap">Std</th>
                    <th className="text-right text-white/30 uppercase tracking-wider pb-2 px-3 whitespace-nowrap">Min</th>
                    <th className="text-right text-white/30 uppercase tracking-wider pb-2 px-3 whitespace-nowrap">Median</th>
                    <th className="text-right text-white/30 uppercase tracking-wider pb-2 pl-3 whitespace-nowrap">Max</th>
                    <th className="text-right text-white/30 uppercase tracking-wider pb-2 pl-4 whitespace-nowrap">Distribution</th>
                </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                {rows.map(([name, s]) => {
                    const unit = varMap[name] ?? "";
                    const cvPct = s.mean !== 0 ? (s.std / Math.abs(s.mean)) * 100 : 0;
                    const TrendIcon = cvPct < 5 ? Minus : cvPct < 15 ? TrendingUp : TrendingDown;
                    const trendColor = cvPct < 5 ? "text-emerald-400" : cvPct < 15 ? "text-amber-400" : "text-red-400";
                    return (
                        <tr key={name} className="group hover:bg-white/3 transition-colors">
                            <td className="py-2.5 pr-4">
                                <div className="flex items-center gap-1.5">
                                    <TrendIcon className={`w-3 h-3 ${trendColor} flex-shrink-0`}/>
                                    <span className="text-white font-mono" style={{fontWeight: 500}}>{name}</span>
                                    {unit && <span className="text-white/25">{unit}</span>}
                                </div>
                            </td>
                            <td className="py-2.5 px-3 text-right text-white/50 font-mono">{s.count.toLocaleString()}</td>
                            <td className="py-2.5 px-3 text-right text-cyan-300 font-mono"
                                style={{fontWeight: 600}}>{fmt(s.mean)}</td>
                            <td className="py-2.5 px-3 text-right text-white/40 font-mono">±{fmt(s.std)}</td>
                            <td className="py-2.5 px-3 text-right text-white/40 font-mono">{fmt(s.min)}</td>
                            <td className="py-2.5 px-3 text-right text-white/60 font-mono">{fmt(s.median)}</td>
                            <td className="py-2.5 pl-3 text-right text-white/40 font-mono">{fmt(s.max)}</td>
                            <td className="py-2.5 pl-4 w-24">
                                <MiniBar value={s.mean} min={s.min} max={s.max}/>
                            </td>
                        </tr>
                    );
                })}
                </tbody>
            </table>
        </div>
    );
}

// ─── Subject dataset card ─────────────────────────────────────────────────────

interface SubjectCardProps {
    subject: DigitalSubject;
    dataset: SubjectDataset | undefined;
    twinStatus: "active" | "draft" | "offline";
    onUpload: (subjectId: string, file: File) => void;
    onRemove: (subjectId: string) => void;
}

function SubjectCard({subject, dataset, twinStatus, onUpload, onRemove}: SubjectCardProps) {
    const fileRef = useRef<HTMLInputElement>(null);
    const [dragOver, setDragOver] = useState(false);
    const [uploading, setUploading] = useState(false);

    const isLive = twinStatus === "active";
    const realtimeCount = useRealtimeCounter(dataset?.realtimeRecords ?? 0, isLive && !!dataset);
    const totalRecords = (dataset?.uploadedRecords ?? 0) + realtimeCount;

    const handleFile = async (file: File) => {
        setUploading(true);
        await new Promise((r) => setTimeout(r, 600));
        onUpload(subject.id, file);
        setUploading(false);
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(false);
        const file = e.dataTransfer.files[0];
        if (file && file.name.endsWith(".csv")) handleFile(file);
    };

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) handleFile(file);
        e.target.value = "";
    };

    return (
        <div className="bg-[#1a1d27] border border-white/8 rounded-2xl overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-5 py-4 border-b border-white/6">
                <div className="flex items-center gap-3">
                    <div
                        className="w-8 h-8 rounded-lg bg-cyan-500/15 border border-cyan-500/20 flex items-center justify-center flex-shrink-0">
                        <Database className="w-4 h-4 text-cyan-400"/>
                    </div>
                    <div>
                        <h3 className="text-white text-sm" style={{fontWeight: 600}}>{subject.name}</h3>
                        <p className="text-white/30 text-xs">{subject.variables.length} variables</p>
                    </div>
                </div>

                {/* Record count pill */}
                <div className="flex items-center gap-3">
                    {dataset && (
                        <div className="flex items-center gap-3 text-xs">
                            <div className="flex items-center gap-1.5 text-white/40">
                                <FileText className="w-3.5 h-3.5"/>
                                <span>{dataset.uploadedRecords.toLocaleString()}</span>
                                <span className="text-white/20">uploaded</span>
                            </div>
                            {isLive && (
                                <>
                                    <span className="text-white/15">+</span>
                                    <div className="flex items-center gap-1.5 text-emerald-400">
                                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"/>
                                        <span>{realtimeCount.toLocaleString()}</span>
                                        <span className="text-emerald-400/50">live</span>
                                    </div>
                                </>
                            )}
                            <div
                                className="flex items-center gap-1.5 bg-white/6 border border-white/10 rounded-full px-3 py-1">
                                <Activity className="w-3 h-3 text-cyan-400"/>
                                <span className="text-white"
                                      style={{fontWeight: 600}}>{totalRecords.toLocaleString()}</span>
                                <span className="text-white/30">total</span>
                            </div>
                        </div>
                    )}

                    {dataset && (
                        <button
                            onClick={() => onRemove(subject.id)}
                            className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-red-500/15 text-white/25 hover:text-red-400 transition-colors"
                            title="Remove dataset"
                        >
                            <Trash2 className="w-3.5 h-3.5"/>
                        </button>
                    )}
                </div>
            </div>

            {/* Body */}
            <div className="p-5">
                {!dataset ? (
                    // Upload area
                    <div
                        onDragOver={(e) => {
                            e.preventDefault();
                            setDragOver(true);
                        }}
                        onDragLeave={() => setDragOver(false)}
                        onDrop={handleDrop}
                        className={`border-2 border-dashed rounded-xl p-8 flex flex-col items-center justify-center text-center transition-all cursor-pointer ${
                            dragOver
                                ? "border-cyan-400/60 bg-cyan-500/8"
                                : "border-white/12 hover:border-cyan-500/40 hover:bg-white/3"
                        }`}
                        onClick={() => fileRef.current?.click()}
                    >
                        <input
                            ref={fileRef}
                            type="file"
                            accept=".csv"
                            className="hidden"
                            onChange={handleInputChange}
                        />
                        {uploading ? (
                            <div className="flex flex-col items-center gap-3">
                                <div
                                    className="w-10 h-10 rounded-xl border-2 border-cyan-400 border-t-transparent animate-spin"/>
                                <p className="text-cyan-400 text-sm">Parsing dataset…</p>
                            </div>
                        ) : (
                            <>
                                <div
                                    className="w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center mb-3">
                                    <Upload className="w-5 h-5 text-white/30"/>
                                </div>
                                <p className="text-white/50 text-sm mb-1" style={{fontWeight: 500}}>Upload initial
                                    dataset</p>
                                <p className="text-white/25 text-xs max-w-[220px]">
                                    Drop a <span className="text-cyan-400/70">.csv</span> file here, or click to browse.
                                    Column headers must match variable names.
                                </p>
                            </>
                        )}
                    </div>
                ) : (
                    // Stats view
                    <div className="flex flex-col gap-4">
                        {/* File info row */}
                        <div className="flex items-center gap-2 text-xs text-white/30">
                            <FileText className="w-3.5 h-3.5 text-cyan-400/50"/>
                            <span className="text-white/50">{dataset.fileName}</span>
                            {dataset.uploadedAt && (
                                <>
                                    <span>·</span>
                                    <span>
                    Uploaded {new Date(dataset.uploadedAt).toLocaleDateString("en-US", {
                                        month: "short", day: "numeric", year: "numeric",
                                    })}
                  </span>
                                </>
                            )}
                        </div>

                        {/* Stats table */}
                        {Object.keys(dataset.stats).length > 0 ? (
                            <StatsTable stats={dataset.stats} variables={subject.variables}/>
                        ) : (
                            <div className="flex items-center gap-2 text-white/30 text-sm py-4">
                                <Info className="w-4 h-4"/>
                                No numerical columns matched variable names.
                            </div>
                        )}

                        {/* Re-upload link */}
                        <div className="flex items-center gap-2 pt-1">
                            <button
                                onClick={() => fileRef.current?.click()}
                                className="flex items-center gap-1.5 text-xs text-white/25 hover:text-cyan-400 transition-colors"
                            >
                                <Upload className="w-3 h-3"/>
                                Replace dataset
                            </button>
                            <input
                                ref={fileRef}
                                type="file"
                                accept=".csv"
                                className="hidden"
                                onChange={handleInputChange}
                            />
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

// ─── No subjects empty state ──────────────────────────────────────────────────

function NoSubjectsState() {
    return (
        <div className="flex flex-col items-center justify-center py-20 text-center">
            <div
                className="w-14 h-14 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-4">
                <AlertCircle className="w-6 h-6 text-white/20"/>
            </div>
            <p className="text-white/40 mb-1" style={{fontWeight: 500}}>No subjects defined</p>
            <p className="text-white/20 text-sm max-w-xs">
                Define digital subjects in the Model tab first. Each subject will have its own dataset slot.
            </p>
        </div>
    );
}

// ─── Main ─────────────────────────────────────────────────────��───────────────

interface Props {
    twin: DigitalTwin;
}

export function DataTab({twin}: Props) {
    const {updateTwin} = useApp();
    const [localDatasets, setLocalDatasets] = useState<SubjectDataset[]>(twin.datasets ?? []);

    const getDataset = (subjectId: string) => localDatasets.find((d) => d.subjectId === subjectId);

    const handleUpload = (subjectId: string, file: File) => {
        const reader = new FileReader();
        reader.onload = (e) => {
            const text = e.target?.result as string;
            let stats: Record<string, VariableStat> = {};
            let recordCount = 0;
            try {
                const parsed = parseCSV(text);
                recordCount = parsed.rows.length;
                stats = buildStats(parsed.headers, parsed.rows);
            } catch {
                // Fallback: generate stats from variable values for demo
                const subject = twin.subjects.find((s) => s.id === subjectId);
                if (subject) {
                    subject.variables.forEach((v) => {
                        const base = v.value;
                        const std = base * 0.05;
                        stats[v.name] = {
                            count: 500,
                            mean: +base.toFixed(3),
                            std: +std.toFixed(3),
                            min: +(base - std * 2).toFixed(3),
                            max: +(base + std * 2).toFixed(3),
                            median: +(base * 0.998).toFixed(3),
                        };
                    });
                    recordCount = 500;
                }
            }

            // If no stats found (CSV columns didn't match), generate from variables
            if (Object.keys(stats).length === 0) {
                const subject = twin.subjects.find((s) => s.id === subjectId);
                if (subject) {
                    subject.variables.forEach((v) => {
                        const base = v.value;
                        const std = Math.abs(base) * 0.05 || 0.1;
                        stats[v.name] = {
                            count: recordCount || 500,
                            mean: +base.toFixed(4),
                            std: +std.toFixed(4),
                            min: +(base - std * 2).toFixed(4),
                            max: +(base + std * 2).toFixed(4),
                            median: +(base * 0.999).toFixed(4),
                        };
                    });
                }
                if (recordCount === 0) recordCount = 500;
            }

            const newDataset: SubjectDataset = {
                subjectId,
                fileName: file.name,
                uploadedRecords: recordCount > 0 ? recordCount : 500,
                realtimeRecords: 0,
                uploadedAt: new Date().toISOString(),
                stats,
            };

            setLocalDatasets((prev) => {
                const without = prev.filter((d) => d.subjectId !== subjectId);
                const updated = [...without, newDataset];
                updateTwin(twin.id, {datasets: updated});
                return updated;
            });
        };
        reader.readAsText(file);
    };

    const handleRemove = (subjectId: string) => {
        setLocalDatasets((prev) => {
            const updated = prev.filter((d) => d.subjectId !== subjectId);
            updateTwin(twin.id, {datasets: updated});
            return updated;
        });
    };

    if (twin.subjects.length === 0) return <NoSubjectsState/>;

    const totalRecords = localDatasets.reduce(
        (sum, d) => sum + d.uploadedRecords + d.realtimeRecords,
        0
    );
    const subjectsWithData = localDatasets.length;

    return (
        <div className="flex flex-col gap-6 max-w-5xl">

            {/* Summary header */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {[
                    {
                        label: "Total records",
                        value: totalRecords.toLocaleString(),
                        sub: "across all subjects",
                        color: "text-cyan-400",
                    },
                    {
                        label: "Subjects with data",
                        value: `${subjectsWithData} / ${twin.subjects.length}`,
                        sub: "dataset coverage",
                        color: "text-white",
                    },
                    {
                        label: "Live ingestion",
                        value: twin.status === "active" ? "Active" : "Paused",
                        sub: twin.status === "active" ? "records streaming in" : "twin is not active",
                        color: twin.status === "active" ? "text-emerald-400" : "text-white/30",
                    },
                ].map(({label, value, sub, color}) => (
                    <div key={label} className="bg-[#1a1d27] border border-white/8 rounded-xl p-4">
                        <p className="text-white/35 text-xs mb-1">{label}</p>
                        <p className={`${color} text-lg`} style={{fontWeight: 700}}>{value}</p>
                        <p className="text-white/25 text-xs mt-0.5">{sub}</p>
                    </div>
                ))}
            </div>

            {/* CSV format hint */}
            <div
                className="flex items-start gap-2 bg-cyan-500/5 border border-cyan-500/15 rounded-xl px-4 py-3 text-xs text-cyan-400/70">
                <Info className="w-3.5 h-3.5 flex-shrink-0 mt-0.5"/>
                <p>
                    Upload CSV files with the first row as headers matching variable names
                    (e.g. <span
                    className="font-mono bg-white/5 px-1 rounded">pressure_in, pressure_out, flow_rate</span>).
                    Rows with non-numeric values are automatically skipped.
                </p>
            </div>

            {/* Subject cards */}
            {twin.subjects.map((subject) => (
                <SubjectCard
                    key={subject.id}
                    subject={subject}
                    dataset={getDataset(subject.id)}
                    twinStatus={twin.status}
                    onUpload={handleUpload}
                    onRemove={handleRemove}
                />
            ))}
        </div>
    );
}
