package markovmusic;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;
import javax.sound.midi.*;

public class MarkovMusic {

    List<Note> output;
    HashMap<List<Note>, List<Note>> optionMap;
    HashMap<List<Note>, List<Integer>> countMap;
    int resolution;
    String[] files = new String[]{"orinoco.mid"};
    int ORDER = 2;
    //Rounding properties of notes. Notes are rounded during prediction so that similar notes are treated the same even if they have slightly different duration/velocity.
    int velocityRounding = 40;
    int durationRounding = 2000;
    int tempoRounding = 1000000000;
    int[] targetChannels = new int[]{};
    int defaultTempo = 500001;

    public static void main(String[] args) {
        MarkovMusic m = new MarkovMusic();
        m.run();
    }

    void run() {
        output = new ArrayList<>();
        optionMap = new HashMap<>();
        countMap = new HashMap<>();
        for (int i = 0; i < files.length; i++) {
            System.out.println("reading file: " + files[i]);
            //Convert from a midi file to a list of parsed notes
            List<Note> notes = readInput(files[i]);
            //Add the notes to the probability map
            addToMap(notes);
            System.out.println(notes.size() + " notes converted");
        }
        System.out.println("\n" + optionMap.size() + " mappings made (order " + ORDER + ")");
        System.out.println("generating music");
        output = generate();
        System.out.println(output.size() + " notes generated");
        writeToFile();
        System.out.println("output written to file");
    }
    
    //Merges all tracks in a sequence into the first track
    void mergeTracks(Sequence sequence) {
        Track[] tracks = sequence.getTracks();
        Track mainTrack = tracks[0];
        for (int i = 1; i < tracks.length; i++) {
            Track t = tracks[i];
            for (int j = 0; j < t.size(); j++) {
                mainTrack.add(t.get(j));
            }
        }

        while (sequence.getTracks().length > 1) {
            sequence.deleteTrack(sequence.getTracks()[1]);
        }
    }

    //Reads through a MIDI file and returns a list of every note in the file
    List<Note> readInput(String fileName) {

        List<Note> mergedTracks = new ArrayList<Note>();
        try {
            Sequence sequence = MidiSystem.getSequence(new File("src/markovmusic/" + fileName));
            //mergeTracks(sequence);
            resolution = sequence.getResolution();
            int trackNumber = 0;
            for (Track track : sequence.getTracks()) {
                trackNumber++;
                System.out.println("Track " + trackNumber + ": size = " + track.size());// ShortMessage.
                List<Note> ls = parseTrack(track);
                mergedTracks.addAll(ls);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Collections.sort(mergedTracks, new Comparator<Note>() {
            @Override
            public int compare(Note obj1, Note obj2) {

                return obj1.timeStamp - obj2.timeStamp;
            }
        });

        for (int i = 0; i < mergedTracks.size() - 1; i++) {
            Note a = mergedTracks.get(i);
            a.nextNoteDelay = mergedTracks.get(i + 1).timeStamp - a.timeStamp;
            mergedTracks.set(i, a);
        }
        return mergedTracks;
    }

    //Returns all the notes contained in a single MIDI track
    List<Note> parseTrack(Track track) {
        List<Note> notes = new ArrayList<>();
        ArrayList<int[]> open = new ArrayList<>();
        List<Long> tempoChanges = new ArrayList<>();
        List<Integer> tempoValues = new ArrayList<>();
        List<Long> instrumentChanges = new ArrayList<>();
        List<Integer> instrumentValues = new ArrayList<>();

        for (int i = 0; i < track.size(); i++) {
            MidiEvent event = track.get(i);
            MidiMessage message = event.getMessage();

            if (message instanceof ShortMessage) {
                ShortMessage sm = (ShortMessage) message;
                if (targetChannels.length == 0 || IntStream.of(targetChannels).anyMatch(x -> x == sm.getChannel())) {
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() != 0) {
                        //Record that this key has been pressed, but not yet released
                        int[] data = new int[]{sm.getData1(), sm.getData2(), (int) event.getTick()};
                        open.add(data);
                    } else if (sm.getCommand() == ShortMessage.NOTE_OFF || (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                        //Find the matching open key, remove it, and add a complete note
                        for (int j = 0; j < open.size(); j++) {
                            if (open.get(j)[0] == sm.getData1()) {
                                Note n = new Note(open.get(j)[0], open.get(j)[1], sm.getData2(), (int) event.getTick() - open.get(j)[2], open.get(j)[2], defaultTempo, 0, open.get(j)[2]);
                                notes.add(n);
                                open.remove(j);
                                break;
                            }
                        }
                    } else if (sm.getCommand() == 0xC0) {
                        //Instrument change command
                        instrumentChanges.add(event.getTick());
                        instrumentValues.add(sm.getData1());
                        //System.out.println("instrument change to: " + sm.getData1() + " " + event.getTick());
                    } else {
                        //System.out.println(sm.getCommand());
                    }
                }
            } else if (message instanceof MetaMessage) {
                MetaMessage mm = (MetaMessage) message;
                if (mm.getType() == 0x51) {
                    //Tempo change command
                    byte[] data = mm.getData();
                    int tempo = (data[0] & 0xff) << 16 | (data[1] & 0xff) << 8 | (data[2] & 0xff);
                    //System.out.println("tempo: " + tempo);
                    tempoChanges.add(event.getTick());
                    tempoValues.add(tempo);
                }
            }

        }

        Collections.sort(notes, new Comparator<Note>() {
            @Override
            public int compare(Note obj1, Note obj2) {

                return obj1.timeStamp - obj2.timeStamp;
            }
        });
        
        //Set the tempo of each note
        for (int i = 0; i < tempoChanges.size(); i++) {
            for (int notePos = 0; notePos < notes.size(); notePos++) {
                Note n = notes.get(notePos);
                if (n.timeStamp > tempoChanges.get(i) && (i == tempoChanges.size() - 1 || n.timeStamp < tempoChanges.get(i + 1))) {
                    n.tempo = tempoValues.get(i);
                } else {
                    break;
                }
            }
        }

        //Set the instrument of each note
        for (int i = 0; i < instrumentChanges.size(); i++) {
            for (int notePos = 0; notePos < notes.size(); notePos++) {
                Note n = notes.get(notePos);
                if (n.timeStamp > instrumentChanges.get(i) && (i == instrumentChanges.size() - 1 || n.timeStamp < instrumentChanges.get(i + 1))) {
                    n.instrument = instrumentValues.get(i);
                } else {
                    break;
                }
            }
        }

        return notes;
    }
    
    //Round a list of notes
    List<Note> roundList(List<Note> list) {
        List<Note> list2 = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            list2.add(list.get(i).rounded(velocityRounding, durationRounding, tempoRounding));
        }
        return list2;
    }

    //Add information from a sequence of notes to the proabbility map
    void addToMap(List<Note> notes) {
        for (int i = 0; i < notes.size(); i++) {
            //Add all subsequences up to the order as matches. ABCD -> ABC:D and BC:D and C:D (order 3). This guarantess a match will be found when generating.
            for (int j = i; j >= 0 && j > i - ORDER; j--) {
                //Use rounded notes for prediction
                List<Note> n = roundList(notes.subList(j, i + 1));
                Note s2;
                if (i == notes.size() - 1) {
                    //null indicates end of sequence. At generation time, if a null note is pulled the sequence will end.
                    s2 = null;
                } else {
                    s2 = notes.get(i + 1);
                }
                if (!optionMap.containsKey(n)) {
                    optionMap.put(n, new ArrayList<>());
                    countMap.put(n, new ArrayList<>());
                }
                List<Note> optionList = optionMap.get(n);
                List<Integer> countList = countMap.get(n);
                int pos = optionList.indexOf(s2);
                //If this pair doesn't exist, add a new entry for it.
                if (pos == -1) {
                    optionList.add(s2);
                    countList.add(1);
                } 
                //Otherwise, just increase the counter
                else {
                    countList.set(pos, countList.get(pos) + 1);
                }
            }
        }
    }

    //Write the generated sequence back out to a MIDI file
    void writeToFile() {
        try {
            //Create a new MIDI sequence 
            Sequence s = new Sequence(javax.sound.midi.Sequence.PPQ, resolution);

            //Obtain a MIDI track from the sequence  
            Track t = s.createTrack();

            //Turn on General MIDI sound set (sysex event)
            byte[] b = {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7};
            SysexMessage sm = new SysexMessage();
            sm.setMessage(b, 6);
            MidiEvent me = new MidiEvent(sm, (long) 0);
            t.add(me);

            MetaMessage mt;

            //set track name (meta event)
            mt = new MetaMessage();
            String TrackName = "midifile track";
            mt.setMessage(0x03, TrackName.getBytes(), TrackName.length());
            me = new MidiEvent(mt, (long) 0);
            t.add(me);

            //set omni on
            ShortMessage mm = new ShortMessage();
            mm.setMessage(0xB0, 0x7D, 0x00);
            me = new MidiEvent(mm, (long) 0);
            t.add(me);

            //set poly on
            mm = new ShortMessage();
            mm.setMessage(0xB0, 0x7F, 0x00);
            me = new MidiEvent(mm, (long) 0);
            t.add(me);

            //set instrument to Piano
            mm = new ShortMessage();
            mm.setMessage(0xC0, 0, 0x00);
            me = new MidiEvent(mm, (long) 0);
            t.add(me);

            int tick = 1;
            int currentTempo = 0;
            int currentInstrument = 0;
            for (Note i : output) {
                if (currentTempo != i.tempo) {
                    mt = new MetaMessage();
                    byte[] bt = {
                        (byte) ((i.tempo >> 16) & 0xFF),
                        (byte) ((i.tempo >> 8) & 0xFF),
                        (byte) ((i.tempo) & 0xFF)};
                    int tempo = (bt[0] & 0xff) << 16 | (bt[1] & 0xff) << 8 | (bt[2] & 0xff);
                    //System.out.println("new tempo: " + tempo);
                    mt.setMessage(0x51, bt, 3);
                    me = new MidiEvent(mt, (long) tick);
                    t.add(me);
                    currentTempo = i.tempo;
                }

                if (currentInstrument != i.instrument) {
                    currentInstrument = i.instrument;
                    mm = new ShortMessage();
                    mm.setMessage(0xC0, i.instrument, 0x00);
                    me = new MidiEvent(mm, (long) tick);
                    t.add(me);
                }

                mm = new ShortMessage();
                mm.setMessage(0x90, i.key, i.startVelocity);
                me = new MidiEvent(mm, (long) tick);
                t.add(me);

                mm = new ShortMessage();
                mm.setMessage(0x80, i.key, i.endVelocity);
                me = new MidiEvent(mm, (long) tick + i.noteDuration);
                t.add(me);

                tick += i.nextNoteDelay;
            }

            //Set end of track (meta event)
            mt = new MetaMessage();
            byte[] bet = {}; 
            mt.setMessage(0x2F, bet, 0);
            me = new MidiEvent(mt, (long) tick + 20);
            t.add(me);

            //Write the MIDI sequence to a MIDI file 
            File f = new File("src/markovmusic/output.mid");
            MidiSystem.write(s, 1, f);
        } 
        catch (Exception e) {
            e.printStackTrace();
        } 
    }

    //Return a random element from a collection
    public static <T> T random(Collection<T> coll) {
        int num = (int) (Math.random() * coll.size());
        for (T t : coll) {
            if (--num < 0) {
                return t;
            }
        }
        throw new AssertionError();
    }

    //Generate a sequence of notes from the probability map
    List<Note> generate() {
        List<Note> current = new ArrayList<>();
        List<Note> rand = random(optionMap.values());
        current.add(rand.get((int) (Math.random() * rand.size())));
        while (true) {
            //Find the furthest back note to include in the subsequence for predicting
            int lowest = Math.max(0, current.size() - ORDER);
            Note add = null;
            //Try progressively smaller subsequences until a match is found in the probability map
            for (int i = 0; i < current.size() - lowest; i++) {
                List<Note> use = roundList(current.subList(lowest + i, current.size()));
                if (optionMap.containsKey(use)) {
                    add = pick(optionMap.get(use), countMap.get(use));
                    break;
                }
            }
            if (add == null) {
                break;
            }
            current.add(add);
        }
        return current;
    }

    //Pick out a random note from a number of weighted options
    Note pick(List<Note> options, List<Integer> counts) {
        int rand = (int) (Math.random() * (double) sumOf(counts)) + 1;

        for (int i = 0; i < options.size(); i++) {
            if (counts.get(i) >= rand) {
                return options.get(i);
            } else {
                rand -= counts.get(i);
            }
        }
        return null;
    }

    int sumOf(List<Integer> arr) {
        int sum = 0;
        for (int i = 0; i < arr.size(); i++) {
            sum += arr.get(i);
        }
        return sum;
    }

}
