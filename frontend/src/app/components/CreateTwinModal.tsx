import {X} from "lucide-react";
import {useState} from "react";

const TWIN_TYPES = [
    "Machine", "Building", "Infrastructure", "Energy System",
    "Pipeline", "Robot", "Sensor Network", "Other",
];

interface CreateTwinModalProps {
    onClose: () => void;
    onCreate: (name: string, description: string, type: string) => void;
}

export function CreateTwinModal({onClose, onCreate}: CreateTwinModalProps) {
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [type, setType] = useState("");

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!name.trim() || !type) return;
        onCreate(name.trim(), description.trim(), type);
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4"
            onClick={(e) => {
                if (e.target === e.currentTarget) onClose();
            }}
        >
            <div className="absolute inset-0 bg-black/60 backdrop-blur-sm"/>
            <div className="relative bg-[#1a1d27] border border-white/10 rounded-2xl w-full max-w-md shadow-2xl">
                {/* Header */}
                <div className="flex items-center justify-between p-5 border-b border-white/8">
                    <div className="flex items-center gap-2.5">
                        <div
                            className="w-7 h-7 rounded-lg bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center">
                            <img src="/icons/icon-512.png" alt="Picota" className="w-4 h-4 object-contain"/>
                        </div>
                        <h2 className="text-white" style={{fontWeight: 600}}>New Digital Twin</h2>
                    </div>
                    <button
                        onClick={onClose}
                        className="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-white/8 text-white/40 hover:text-white/70 transition-colors"
                    >
                        <X className="w-4 h-4"/>
                    </button>
                </div>

                {/* Form */}
                <form onSubmit={handleSubmit} className="p-5 flex flex-col gap-4">
                    <div>
                        <label className="text-white/50 text-xs uppercase tracking-wider mb-2 block">
                            Twin Name <span className="text-cyan-500">*</span>
                        </label>
                        <input
                            type="text"
                            placeholder="e.g. Pump Station Alpha"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white placeholder-white/25 text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/30 transition-all"
                            autoFocus
                        />
                    </div>

                    <div>
                        <label className="text-white/50 text-xs uppercase tracking-wider mb-2 block">
                            Asset Type <span className="text-cyan-500">*</span>
                        </label>
                        <div className="flex flex-wrap gap-2">
                            {TWIN_TYPES.map((t) => (
                                <button
                                    key={t}
                                    type="button"
                                    onClick={() => setType(t)}
                                    className={`px-3 py-1.5 rounded-lg text-sm border transition-all ${
                                        type === t
                                            ? "bg-cyan-500/20 border-cyan-500/60 text-cyan-300"
                                            : "bg-white/4 border-white/10 text-white/40 hover:border-white/20 hover:text-white/60"
                                    }`}
                                >
                                    {t}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div>
                        <label className="text-white/50 text-xs uppercase tracking-wider mb-2 block">
                            Description <span className="text-white/25">(optional)</span>
                        </label>
                        <textarea
                            placeholder="Brief description of the physical asset or system..."
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            rows={3}
                            className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white placeholder-white/25 text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/30 transition-all resize-none"
                        />
                    </div>

                    <p className="text-white/25 text-xs">
                        The twin will be created as a <span className="text-amber-400">draft</span>. You'll be taken to
                        the detail page to fill in the model and configuration.
                    </p>

                    <div className="flex items-center gap-3 pt-1">
                        <button
                            type="button"
                            onClick={onClose}
                            className="flex-1 py-2.5 rounded-xl border border-white/10 text-white/50 hover:text-white/70 text-sm hover:bg-white/5 transition-colors"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={!name.trim() || !type}
                            className="flex-1 py-2.5 rounded-xl bg-cyan-500 hover:bg-cyan-400 disabled:opacity-40 disabled:cursor-not-allowed text-white text-sm transition-colors shadow-lg shadow-cyan-500/20"
                            style={{fontWeight: 500}}
                        >
                            Create & open
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
