package markovmusic;

public class Note {

    public int key;
    public int startVelocity;
    public int endVelocity;
    public int noteDuration;
    public int nextNoteDelay;
    public int tempo;
    public int instrument;
    public int timeStamp;

    public Note(int key, int startVelocity, int endVelocity, int noteDuration, int nextNoteDelay, int tempo, int instrument, int timeStamp) {
        this.key = key;
        this.startVelocity = startVelocity;
        this.endVelocity = endVelocity;
        this.noteDuration = noteDuration;
        this.nextNoteDelay = nextNoteDelay;
        this.tempo = tempo;
        this.instrument = instrument;
        this.timeStamp = timeStamp;
    }

    @Override
    public String toString() {
        String val = "k: " + key + " sv: " + startVelocity + " ev: " + endVelocity + " nd: " + noteDuration + " nnd: " + nextNoteDelay;
        return val;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Note) {
            Note n = (Note) o;
            return (key == n.key && startVelocity == n.startVelocity && endVelocity == n.endVelocity && noteDuration == n.noteDuration && nextNoteDelay == n.nextNoteDelay && tempo == n.tempo && instrument == n.instrument);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + this.key;
        hash = 67 * hash + this.startVelocity;
        hash = 67 * hash + this.endVelocity;
        hash = 67 * hash + this.noteDuration;
        hash = 67 * hash + this.nextNoteDelay;
        hash = 67 * hash + this.tempo;
        hash = 67 * hash + this.instrument;
        return hash;
    }
    
    public Note rounded(int velocityRounding, int durationRounding, int tempoRounding) {
        return new Note(this.key, this.startVelocity - (this.startVelocity % velocityRounding), this.endVelocity - (this.endVelocity % velocityRounding), this.noteDuration - (this.noteDuration % durationRounding), this.nextNoteDelay - (this.nextNoteDelay % durationRounding), this.tempo - (this.tempo % tempoRounding), instrument, timeStamp);
    }
}
