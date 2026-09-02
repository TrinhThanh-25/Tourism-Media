package com.example.tourismmedia.ui.challenges;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Challenge;
import java.util.Locale;

public class ChallengesFragment extends Fragment {
    private AppRepository repo;
    private ChallengeAdapter adapter;
    public ChallengesFragment() { super(R.layout.fragment_challenges); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        repo = AppRepository.get(requireContext());
        RecyclerView list = view.findViewById(R.id.challenges_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChallengeAdapter(this::detail);
        list.setAdapter(adapter);
        repo.myChallenges((data, error) -> {
            if (error == null) adapter.submit(data);
            else repo.challenges((publicData, publicError) -> adapter.submit(publicData));
        });
        repo.points((balance, error) -> {
            if (balance != null) ((TextView) view.findViewById(R.id.challenge_points)).setText(String.format(Locale.getDefault(), "%,d", balance.points));
        });
        view.findViewById(R.id.open_rewards).setOnClickListener(v -> open("rewards", 0));
        view.findViewById(R.id.challenge_trophy).setOnClickListener(v -> open("rewards", 0));
    }

    private void detail(Challenge challenge) { open("challenge-detail", challenge.id); }
    private void open(String mode, long id) {
        Bundle args = new Bundle(); args.putString("mode", mode); args.putLong("id", id);
        Navigation.findNavController(requireView()).navigate(R.id.member3WorkspaceFragment, args);
    }
}
