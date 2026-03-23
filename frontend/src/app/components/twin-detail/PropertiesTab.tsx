import {useState} from "react";
import {DigitalTwin, TwinStatus, useApp} from "../../context/AppContext";
import {Power, Save, Check, Tag, FileText, Hash, Coins, Info} from "lucide-react";

const STATUS_OPTIONS: { value: TwinStatus; label: string; dot: string; text: string }[] = [
    {value: "active", label: "Active", dot: "bg-emerald-400", text: "text-emerald-400"},
    {value: "draft", label: "Draft", dot: "bg-amber-400", text: "text-amber-400"},
    {value: "offline", label: "Offline", dot: "bg-white/25", text: "text-white/40"},
];

interface Props {
    twin: DigitalTwin;
}

export function PropertiesTab({twin}: Props) {
    const {updateTwin} = useApp();
    const [name, setName] = useState(twin.name);
    const [description, setDescription] = useState(twin.description);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    const currentStatus = STATUS_OPTIONS.find((s) => s.value === twin.status)!;
    const isActive = twin.status === "active";

    const handleToggleActive = () => {
        const next: TwinStatus = isActive ? "offline" : "active";
        updateTwin(twin.id, {status: next});
    };

    const handleSave = async () => {
        setSaving(true);
        await new Promise((r) => setTimeout(r, 600));
        updateTwin(twin.id, {name, description});
        setSaving(false);
        setSaved(true);
        setTimeout(() => setSaved(false), 2500);
    };

    return (
        <div className="flex flex-col gap-5 max-w-2xl">
            {/* Activate / deactivate banner */}
            <div className={`flex items-center justify-between p-4 rounded-xl border ${
                isActive
                    ? "bg-emerald-500/8 border-emerald-500/20"
                    : "bg-white/4 border-white/8"
            }`}>
                <div className="flex items-center gap-3">
                    <span className={`w-2 h-2 rounded-full ${currentStatus.dot} ${isActive ? "animate-pulse" : ""}`}/>
                    <div>
                        <p className={`text-sm ${currentStatus.text}`} style={{fontWeight: 600}}>
                            {currentStatus.label}
                        </p>
                        <p className="text-white/30 text-xs">
                            {isActive ? "Twin is running and consuming credits" : "Twin is not active"}
                        </p>
                    </div>
                </div>
                <button
                    onClick={handleToggleActive}
                    className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm transition-all ${
                        isActive
                            ? "bg-red-500/10 hover:bg-red-500/20 border border-red-500/25 text-red-400"
                            : "bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/25 text-emerald-400"
                    }`}
                    style={{fontWeight: 500}}
                >
                    <Power className="w-3.5 h-3.5"/>
                    {isActive ? "Deactivate" : "Activate"}
                </button>
            </div>

            {/* Metadata fields */}
            <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5 flex flex-col gap-4">
                <h3 className="text-white/70 text-xs uppercase tracking-wider" style={{fontWeight: 600}}>
                    Metadata
                </h3>

                <div>
                    <label className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                        <FileText className="w-3.5 h-3.5"/> Name
                    </label>
                    <input
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                    />
                </div>

                <div>
                    <label className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                        <Info className="w-3.5 h-3.5"/> Description
                    </label>
                    <textarea
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        rows={3}
                        className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all resize-none"
                    />
                </div>

                <div className="grid grid-cols-2 gap-4">
                    <div>
                        <label
                            className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                            <Hash className="w-3.5 h-3.5"/> Version
                        </label>
                        <p className="text-white/60 text-sm px-3.5 py-2.5 bg-white/3 border border-white/6 rounded-xl">
                            v{twin.version}
                        </p>
                    </div>
                    <div>
                        <label
                            className="text-white/40 text-xs uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                            <Tag className="w-3.5 h-3.5"/> Asset type
                        </label>
                        <p className="text-white/60 text-sm px-3.5 py-2.5 bg-white/3 border border-white/6 rounded-xl">
                            {twin.type}
                        </p>
                    </div>
                </div>

                {/* Status selector */}
                <div>
                    <label className="text-white/40 text-xs uppercase tracking-wider mb-2 block">Status</label>
                    <div className="flex gap-2">
                        {STATUS_OPTIONS.map((opt) => (
                            <button
                                key={opt.value}
                                onClick={() => updateTwin(twin.id, {status: opt.value})}
                                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs transition-all ${
                                    twin.status === opt.value
                                        ? `${opt.text} bg-white/8 border-white/20`
                                        : "text-white/30 border-white/8 hover:border-white/15 hover:text-white/50"
                                }`}
                            >
                                <span className={`w-1.5 h-1.5 rounded-full ${opt.dot}`}/>
                                {opt.label}
                            </button>
                        ))}
                    </div>
                </div>

                <button
                    onClick={handleSave}
                    disabled={saving}
                    className="self-start flex items-center gap-2 bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white px-4 py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/15"
                    style={{fontWeight: 500}}
                >
                    {saved ? <><Check className="w-4 h-4"/>Saved</> : saving ? "Saving…" : <><Save className="w-4 h-4"/>Save
                        changes</>}
                </button>
            </div>

            {/* Credits */}
            <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-5">
                <h3 className="text-white/70 text-xs uppercase tracking-wider mb-4" style={{fontWeight: 600}}>
                    Resource usage
                </h3>
                <div className="flex items-center gap-3">
                    <div
                        className="w-10 h-10 rounded-xl bg-amber-500/15 border border-amber-500/20 flex items-center justify-center">
                        <Coins className="w-5 h-5 text-amber-400"/>
                    </div>
                    <div>
                        <p className="text-white" style={{fontWeight: 700, fontSize: "1.3rem"}}>
                            {twin.creditsUsed.toLocaleString()}
                        </p>
                        <p className="text-white/35 text-xs">credits consumed by this twin</p>
                    </div>
                </div>
            </div>
        </div>
    );
}
